package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import kr.hhplus.be.server.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 API 통합 테스트
 * 
 * Why: 쿠폰 발급부터 사용까지의 전체 플로우가 비즈니스 요구사항을 만족하는지 검증
 * How: 실제 고객의 쿠폰 사용 시나리오를 반영한 API 레벨 테스트
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("쿠폰 API 통합 시나리오")
public class CouponTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepositoryPort userRepositoryPort;
    @Autowired private CouponRepositoryPort couponRepositoryPort;
    @Autowired private CouponHistoryRepositoryPort couponHistoryRepositoryPort;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 데이터를 생성하여 OptimisticLocking 충돌 회피
    }

    @Test
    @DisplayName("고객이 사용 가능한 쿠폰을 발급받을 수 있다")
    void customerCanIssueAvailableCoupon() throws Exception {
        // Given
        User customer = createUniqueCustomer("사용가능쿠폰고객");
        Coupon coupon = createUniqueAvailableCoupon("AVAILABLE_COUPON");
        
        CouponRequest request = createCouponRequest(customer.getId(), coupon.getId());

        // When & Then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customer.getId()))
            .andExpect(jsonPath("$.data.couponId").value(coupon.getId()));
    }

    @Test
    @DisplayName("만료된 쿠폰은 발급받을 수 없다")
    void cannotIssueExpiredCoupon() throws Exception {
        // Given
        User customer = createUniqueCustomer("만료쿠폰고객");
        Coupon expiredCoupon = createUniqueExpiredCoupon("EXPIRED_COUPON");
        
        CouponRequest request = createCouponRequest(customer.getId(), expiredCoupon.getId());

        // When & Then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 고객은 쿠폰을 발급받을 수 없다")
    void nonExistentCustomerCannotIssueCoupon() throws Exception {
        // Given
        Coupon coupon = createUniqueAvailableCoupon("AVAILABLE_COUPON");
        CouponRequest request = createCouponRequest(999L, coupon.getId());

        // When & Then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급받을 수 없다")
    void nonExistentCouponCannotBeIssued() throws Exception {
        // Given
        User customer = createUniqueCustomer("비쿠폰고객");
        CouponRequest request = createCouponRequest(customer.getId(), 999L);

        // When & Then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("고객이 자신이 보유한 쿠폰 목록을 조회할 수 있다")
    void customerCanViewTheirCoupons() throws Exception {
        // Given
        User customer = createUniqueCustomer("목록조회고객");
        Coupon coupon = createUniqueAvailableCoupon("MY_COUPON");
        
        CouponRequest issueRequest = createCouponRequest(customer.getId(), coupon.getId());
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(issueRequest)));

        // When & Then
        mockMvc.perform(get("/api/coupon/user/{userId}", customer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("쿠폰이 없는 고객은 빈 목록을 받는다")
    void customerWithNoCouponsGetsEmptyList() throws Exception {
        // Given
        User newCustomer = createUniqueCustomer("신규고객");

        // When & Then
        mockMvc.perform(get("/api/coupon/user/{userId}", newCustomer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 쿠폰 조회는 실패한다")
    void nonExistentCustomerCouponQueryFails() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupon/user/{userId}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("한정 수량 쿠폰에 대한 동시 발급에서 수량 한도를 초과하지 않는다")
    void maintainsCouponLimitUnderConcurrentIssuance() throws Exception {
        // Given - 5개 한정 쿠폰에 10명의 고객이 동시 발급 시도
        Coupon limitedCoupon = createUniqueLimitedCoupon("LIMITED_COUPON", 5);
        List<User> customers = createMultipleCustomers(10);

        // When - 10명이 동시에 쿠폰 발급 시도
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                try {
                    User customer = customers.get((int)(Math.random() * customers.size()));
                    CouponRequest request = createCouponRequest(customer.getId(), limitedCoupon.getId());

                    var response = mockMvc.perform(post("/api/coupon/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();

                    return response.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });

        // Then - 최대 5개 쿠폰만 발급 성공
        assertThat(result.getTotalCount()).isEqualTo(10);
        
        Coupon finalCoupon = couponRepositoryPort.findById(limitedCoupon.getId()).orElseThrow();
        assertThat(finalCoupon.getIssuedCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("동일 고객의 동일 쿠폰 중복 발급 시도는 차단된다")
    void preventsDuplicateCouponIssuanceToSameCustomer() throws Exception {
        // Given
        User customer = createUniqueCustomer("중복발급고객");
        Coupon coupon = createUniqueAvailableCoupon("DUPLICATE_TEST_COUPON");
        
        CouponRequest request = createCouponRequest(customer.getId(), coupon.getId());
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // When & Then - 같은 쿠폰 재발급 시도
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_ALREADY_ISSUED.getCode()));
    }

    // === 헬퍼 메서드 ===
    
    private CouponRequest createCouponRequest(Long userId, Long couponId) {
        CouponRequest request = new CouponRequest();
        request.setUserId(userId);
        request.setCouponId(couponId);
        return request;
    }
    
    private User createUniqueCustomer(String name) {
        return userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser()
                .name(name + "_" + System.nanoTime())
                .build()
        );
    }
    
    private Coupon createUniqueAvailableCoupon(String code) {
        return couponRepositoryPort.save(
            TestBuilder.CouponBuilder.defaultCoupon()
                .code(code + "_" + System.nanoTime())
                .withQuantity(100, 0)
                .build()
        );
    }
    
    private Coupon createUniqueExpiredCoupon(String code) {
        return couponRepositoryPort.save(
            TestBuilder.CouponBuilder.expiredCoupon()
                .code(code + "_" + System.nanoTime())
                .build()
        );
    }
    
    private Coupon createUniqueLimitedCoupon(String code, int maxQuantity) {
        return couponRepositoryPort.save(
            TestBuilder.CouponBuilder.defaultCoupon()
                .code(code + "_" + System.nanoTime())
                .withQuantity(maxQuantity, 0)
                .build()
        );
    }
    
    private List<User> createMultipleCustomers(int count) {
        List<User> customers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            customers.add(createUniqueCustomer("고객" + i));
        }
        return customers;
    }
}