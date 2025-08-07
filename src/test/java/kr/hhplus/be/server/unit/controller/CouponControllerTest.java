package kr.hhplus.be.server.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.facade.coupon.GetCouponListFacade;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController 비즈니스 시나리오 및 Bean Validation 테스트
 * 
 * Why: 쿠폰 컨트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(CouponController.class)
@DisplayName("쿠폰 컨트롤러 API 및 Validation 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IssueCouponFacade issueCouponFacade;

    @MockBean
    private GetCouponListFacade getCouponListFacade;

    @MockBean
    private kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort couponRepositoryPort;

    @Test
    @DisplayName("고객이 쿠폰을 성공적으로 발급받는다")
    void issueCoupon_Success() throws Exception {
        // given - 고객이 이벤트 페이지에서 쿠폰을 발급받는 상황
        Long customerId = 1L;
        Long couponId = 1L;
        CouponRequest request = new CouponRequest(customerId, couponId);

        CouponHistory issuedCoupon = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(couponId)
                .status(CouponHistoryStatus.ISSUED)
                .build();

        when(issueCouponFacade.issueCoupon(customerId, couponId)).thenReturn(issuedCoupon);
        
        // Mock the coupon entity
        Coupon mockCoupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .code("DISCOUNT_10")
                .discountRate(new BigDecimal("0.10"))
                .status(CouponStatus.ACTIVE)
                .build();
        when(couponRepositoryPort.findById(couponId)).thenReturn(java.util.Optional.of(mockCoupon));

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.couponId").value(couponId));
    }

    static Stream<Arguments> provideInvalidCouponRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, 1L, "사용자 ID가 null"),
                // couponId가 null
                Arguments.of(1L, null, "쿠폰 ID가 null"),
                // userId가 음수
                Arguments.of(-1L, 1L, "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, 1L, "사용자 ID가 0"),
                // couponId가 음수
                Arguments.of(1L, -1L, "쿠폰 ID가 음수"),
                // couponId가 0
                Arguments.of(1L, 0L, "쿠폰 ID가 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidCouponRequests")
    @DisplayName("유효하지 않은 쿠폰 발급 요청 시 Bean Validation 에러가 발생한다")
    void issueCoupon_InvalidRequest_ValidationError(Long userId, Long couponId, String description) throws Exception {
        // given
        CouponRequest request = new CouponRequest(userId, couponId);

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 요청 본문으로 쿠폰 발급 시 validation 에러가 발생한다")
    void issueCoupon_EmptyBody_ValidationError() throws Exception {
        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외가 발생한다")
    void issueCoupon_CouponNotFound() throws Exception {
        // given - 존재하지 않는 쿠폰 발급 시도
        Long customerId = 1L;
        Long invalidCouponId = 999L;
        CouponRequest request = new CouponRequest(customerId, invalidCouponId);

        when(issueCouponFacade.issueCoupon(customerId, invalidCouponId))
                .thenThrow(new CouponException.NotFound());

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("고객의 쿠폰 목록을 성공적으로 조회한다")
    void getCoupons_Success() throws Exception {
        // given - 고객이 마이페이지에서 쿠폰 목록을 확인하는 상황
        Long customerId = 1L;
        int limit = 10;
        int offset = 0;

        List<CouponHistory> couponHistories = List.of(
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .userId(customerId)
                        .status(CouponHistoryStatus.ISSUED)
                        .build()
        );

        when(getCouponListFacade.getCouponList(customerId, limit, offset))
                .thenReturn(couponHistories);
        
        // Mock coupon entity for response
        Coupon mockCoupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(1L)
                .code("WELCOME10")
                .discountRate(new BigDecimal("0.10"))
                .status(CouponStatus.ACTIVE)
                .build();
        when(couponRepositoryPort.findById(anyLong())).thenReturn(java.util.Optional.of(mockCoupon));

        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").value(customerId));
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                // limit이 음수
                Arguments.of(1L, -1, 0, "limit이 음수"),
                // limit이 0
                Arguments.of(1L, 0, 0, "limit이 0"),
                // limit이 너무 큼 (100 초과)
                Arguments.of(1L, 101, 0, "limit이 100 초과"),
                // offset이 음수
                Arguments.of(1L, 10, -1, "offset이 음수")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("유효하지 않은 페이지네이션 파라미터로 쿠폰 조회 시 validation 에러가 발생한다")
    void getCoupons_InvalidPagination_ValidationError(Long userId, int limit, int offset, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", userId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    static Stream<Arguments> provideInvalidUserIdsForGet() {
        return Stream.of(
                Arguments.of(-1L, "음수 사용자 ID"),
                Arguments.of(0L, "0인 사용자 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIdsForGet")
    @DisplayName("유효하지 않은 사용자 ID로 쿠폰 조회 시 validation 에러가 발생한다")
    void getCoupons_InvalidUserId_ValidationError(Long invalidUserId, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", invalidUserId)
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }
}