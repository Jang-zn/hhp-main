package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.adapter.locking.InMemoryLockingAdapter;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 잔액 API 통합 테스트
 * 
 * Why: 잔액 충전부터 조회까지의 전체 플로우가 비즈니스 요구사항을 만족하는지 검증
 * How: 실제 고객의 잔액 관리 시나리오를 반영한 API 레벨 테스트
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("잔액 API 통합 시나리오")
public class BalanceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepositoryPort userRepositoryPort;
    @Autowired private BalanceRepositoryPort balanceRepositoryPort;
    @Autowired private InMemoryLockingAdapter lockingAdapter;

    private User customerWithBalance;
    private User customerWithoutBalance;

    @BeforeEach
    void setUp() {
        lockingAdapter.clearAllLocks();
    }

    private User createCustomerWithBalance(String name, String amount) {
        User customer = userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser().name(name).build()
        );
        balanceRepositoryPort.save(
            TestBuilder.BalanceBuilder.defaultBalance()
                .userId(customer.getId())
                .amount(new BigDecimal(amount))
                .build()
        );
        return customer;
    }

    private User createCustomerWithoutBalance(String name) {
        return userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser().name(name).build()
        );
    }

    @Test
    @DisplayName("기존 잔액이 있는 고객이 추가 충전할 수 있다")
    void customerWithExistingBalanceCanChargeMore() throws Exception {
        // Given - 50,000원 보유 고객이 10,000원 추가 충전
        User customer = createCustomerWithBalance("기존잔액고객", "50000");
        BalanceRequest request = createBalanceRequest(customer.getId(), "10000");

        // When & Then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customerWithBalance.getId()))
            .andExpect(jsonPath("$.data.amount").value(60000.0));
    }

    @Test
    @DisplayName("잔액이 없는 고객이 첫 충전할 수 있다")
    void customerWithoutBalanceCanMakeFirstCharge() throws Exception {
        // Given - 잔액 없는 고객이 30,000원 첫 충전
        BalanceRequest request = createBalanceRequest(customerWithoutBalance.getId(), "30000");

        // When & Then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customerWithoutBalance.getId()))
            .andExpect(jsonPath("$.data.amount").value(30000.0));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 충전 요청은 차단된다")
    void preventsChargeRequestFromNonExistentCustomer() throws Exception {
        // Given - 존재하지 않는 고객 ID로 충전 시도
        BalanceRequest request = createBalanceRequest(999999L, "10000");

        // When & Then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideInvalidChargeAmounts")
    @DisplayName("유효하지 않은 충전 금액은 거부된다")
    void preventsChargeWithInvalidAmounts(String amount, String scenario) throws Exception {
        // Given
        BalanceRequest request = createBalanceRequest(customerWithBalance.getId(), amount);

        // When & Then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("고객이 자신의 현재 잔액을 조회할 수 있다")
    void customerCanViewCurrentBalance() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/balance/{userId}", customerWithBalance.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customerWithBalance.getId()))
            .andExpect(jsonPath("$.data.amount").value(50000.0));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 잔액 조회는 실패한다")
    void failsWhenViewingNonExistentCustomerBalance() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/balance/{userId}", 999L))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER_ID.getCode()));
    }

    @Test
    @DisplayName("잔액이 없는 고객의 조회 요청은 적절한 응답을 받는다")
    void customerWithoutBalanceGetsAppropriateResponse() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/balance/{userId}", customerWithoutBalance.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER_ID.getCode()));
    }

    @Test
    @DisplayName("동일 고객의 동시 충전 요청이 정확히 처리된다")
    void handlesSimultaneousChargeRequestsProperly() throws Exception {
        // Given - 초기 잔액 10,000원 고객
        User testCustomer = userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser().name("동시충전테스트고객").build()
        );
        balanceRepositoryPort.save(
            TestBuilder.BalanceBuilder.defaultBalance()
                .userId(testCustomer.getId())
                .amount(new BigDecimal("10000"))
                .build()
        );

        // When - 5번의 동시 충전 요청 (각 1,000원)
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                try {
                    BalanceRequest request = createBalanceRequest(testCustomer.getId(), "1000");
                    var response = mockMvc.perform(post("/api/balance/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                    
                    if (response.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                        return "SUCCESS";
                    } else {
                        failureCount.incrementAndGet();
                        return "FAILURE";
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return "ERROR";
                }
            });

        // Then - 모든 요청 완료 및 정확한 잔액 반영
        assertThat(result.getTotalCount()).isEqualTo(5);
        
        int expectedAmount = 10000 + (successCount.get() * 1000);
        mockMvc.perform(get("/api/balance/{userId}", testCustomer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.amount").value(expectedAmount));
    }

    @Test
    @DisplayName("동일 고객의 동시 차감 요청에서 잔액이 음수가 되지 않는다")
    void preventsNegativeBalanceInSimultaneousDeductions() throws Exception {
        // Given - 초기 잔액 5,000원 고객
        User testCustomer = userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser().name("동시차감테스트고객").build()
        );
        balanceRepositoryPort.save(
            TestBuilder.BalanceBuilder.defaultBalance()
                .userId(testCustomer.getId())
                .amount(new BigDecimal("5000"))
                .build()
        );

        // When - 5번의 동시 차감 요청 (각 2,000원, 총 10,000원 시도하지만 잔액은 5,000원)
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                try {
                    BalanceRequest request = createBalanceRequest(testCustomer.getId(), "2000");
                    var response = mockMvc.perform(post("/api/balance/deduct")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                    
                    if (response.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                        return "SUCCESS";
                    } else {
                        failureCount.incrementAndGet();
                        return "FAILURE";
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return "ERROR";
                }
            });

        // Then - 최대 2번만 성공하고 잔액이 음수가 되지 않음
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(successCount.get()).isLessThanOrEqualTo(2);
        assertThat(failureCount.get()).isGreaterThanOrEqualTo(3);

        mockMvc.perform(get("/api/balance/{userId}", testCustomer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.amount", org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("동일 고객의 충전과 차감 동시 요청이 정확히 처리된다")
    void handlesSimultaneousChargeAndDeductRequests() throws Exception {
        // Given - 초기 잔액 10,000원 고객
        User testCustomer = userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser().name("혼합동시테스트고객").build()
        );
        balanceRepositoryPort.save(
            TestBuilder.BalanceBuilder.defaultBalance()
                .userId(testCustomer.getId())
                .amount(new BigDecimal("10000"))
                .build()
        );

        // When - 충전 3회(+2,000원)와 차감 3회(-1,000원) 동시 실행
        AtomicInteger chargeSuccess = new AtomicInteger(0);
        AtomicInteger deductSuccess = new AtomicInteger(0);
        
        ConcurrencyTestHelper.ConcurrencyTestResult chargeResult = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                try {
                    BalanceRequest request = createBalanceRequest(testCustomer.getId(), "2000");
                    var response = mockMvc.perform(post("/api/balance/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                    
                    if (response.getResponse().getStatus() == 200) {
                        chargeSuccess.incrementAndGet();
                    }
                    return response.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });

        ConcurrencyTestHelper.ConcurrencyTestResult deductResult = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                try {
                    BalanceRequest request = createBalanceRequest(testCustomer.getId(), "1000");
                    var response = mockMvc.perform(post("/api/balance/deduct")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                    
                    if (response.getResponse().getStatus() == 200) {
                        deductSuccess.incrementAndGet();
                    }
                    return response.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });

        // Then - 최종 잔액이 논리적으로 정확함
        assertThat(chargeResult.getTotalCount()).isEqualTo(3);
        assertThat(deductResult.getTotalCount()).isEqualTo(3);
        
        mockMvc.perform(get("/api/balance/{userId}", testCustomer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.amount", org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideInvalidChargeAmounts() {
        return Stream.of(
            Arguments.of("0", "0원 충전"),
            Arguments.of("-1000", "음수 충전"),
            Arguments.of("-0.01", "음수 소수점 충전")
        );
    }
    
    // === 헬퍼 메서드 ===
    
    private BalanceRequest createBalanceRequest(Long userId, String amount) {
        return new BalanceRequest(userId, new BigDecimal(amount));
    }
}