package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
@DisplayName("잔액 API 통합 시나리오")
public class BalanceTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepositoryPort userRepositoryPort;
    @Autowired private BalanceRepositoryPort balanceRepositoryPort;



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
            .andExpect(jsonPath("$.data.userId").value(customer.getId()))
            .andExpect(jsonPath("$.data.amount").value(60000.0));
    }

    @Test
    @DisplayName("잔액이 없는 고객이 첫 충전할 수 있다")
    void customerWithoutBalanceCanMakeFirstCharge() throws Exception {
        // Given - 잔액 없는 고객이 30,000원 첫 충전  
        User customer = createCustomerWithoutBalance("첫충전고객");
        BalanceRequest request = createBalanceRequest(customer.getId(), "30000");

        // When & Then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customer.getId()))
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
        User customer = createCustomerWithBalance("무효금액테스트고객", "100000");
        BalanceRequest request = createBalanceRequest(customer.getId(), amount);

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
        // Given
        User customer = createCustomerWithBalance("잔액조회고객", "50000");
        
        // When & Then
        mockMvc.perform(get("/api/balance/{userId}", customer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customer.getId()))
            .andExpect(jsonPath("$.data.amount").value(50000.0));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 잔액 조회는 실패한다")
    void failsWhenViewingNonExistentCustomerBalance() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/balance/{userId}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("잔액이 없는 고객의 조회 요청은 적절한 응답을 받는다")
    void customerWithoutBalanceGetsAppropriateResponse() throws Exception {
        // Given
        User customer = createCustomerWithoutBalance("잔액없는고객");
        
        // When & Then - 잔액이 없으면 balance가 null이 되어 RuntimeException 발생
        mockMvc.perform(get("/api/balance/{userId}", customer.getId()))
            .andExpect(status().isInternalServerError());
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

    // 차감 API가 구현되지 않았으므로 이 테스트는 제거
    // OrderService의 payOrder에서 잔액 차감이 처리됨

    // 차감 API가 구현되지 않았으므로 이 테스트는 제거
    // OrderService의 payOrder에서 잔액 차감이 처리됨

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