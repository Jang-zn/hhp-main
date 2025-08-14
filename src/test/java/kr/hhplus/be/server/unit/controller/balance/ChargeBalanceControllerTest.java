package kr.hhplus.be.server.unit.controller.balance;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.service.BalanceService;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BalanceController.chargeBalance 메서드 테스트
 * 
 * Why: 잔액 충전 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(BalanceController.class)
@DisplayName("잔액 충전 컨트롤러 API")
class ChargeBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    
    @MockitoBean
    private BalanceService balanceService;

    @Test
    @DisplayName("고객이 자신의 계정에 잔액을 성공적으로 충전한다")
    void chargeBalance_Success() throws Exception {
        // given - 고객이 마이페이지에서 잔액을 충전하는 상황
        Long customerId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest chargeRequest = new BalanceRequest(customerId, chargeAmount);

        Balance chargedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("150000")) // 기존 100000 + 충전 50000
                .build();
        
        when(balanceService.chargeBalance(customerId, chargeAmount)).thenReturn(chargedBalance);

        // when & then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chargeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.amount").value(150000));
    }

    static Stream<Arguments> provideInvalidBalanceRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, new BigDecimal("10000"), "사용자 ID가 null"),
                // amount가 null
                Arguments.of(1L, null, "금액이 null"),
                // userId가 음수
                Arguments.of(-1L, new BigDecimal("10000"), "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, new BigDecimal("10000"), "사용자 ID가 0"),
                // amount가 최소값 미만 (1000 미만)
                Arguments.of(1L, new BigDecimal("999"), "금액이 최소값 미만"),
                // amount가 최대값 초과 (1000000 초과)
                Arguments.of(1L, new BigDecimal("1000001"), "금액이 최대값 초과"),
                // amount가 음수
                Arguments.of(1L, new BigDecimal("-1000"), "금액이 음수"),
                // amount가 0
                Arguments.of(1L, BigDecimal.ZERO, "금액이 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidBalanceRequests")
    @DisplayName("유효하지 않은 잔액 충전 요청 시 Bean Validation 에러가 발생한다")
    void chargeBalance_InvalidRequest_ValidationError(Long userId, BigDecimal amount, String description) throws Exception {
        // given
        BalanceRequest request = new BalanceRequest(userId, amount);

        // when & then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 요청 본문으로 잔액 충전 시 validation 에러가 발생한다")
    void chargeBalance_EmptyBody_ValidationError() throws Exception {
        // when & then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 잔액 충전 시 예외가 발생한다")
    void chargeBalance_UserNotFound() throws Exception {
        // given - 탈퇴했거나 존재하지 않는 사용자의 충전 시도
        Long invalidUserId = 999L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest chargeRequest = new BalanceRequest(invalidUserId, chargeAmount);

        when(balanceService.chargeBalance(invalidUserId, chargeAmount))
                .thenThrow(new UserException.NotFound());

        // when & then
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chargeRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }
}