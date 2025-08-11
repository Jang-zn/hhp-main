package kr.hhplus.be.server.unit.controller.balance;

import kr.hhplus.be.server.api.controller.BalanceController;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BalanceController.getBalance 메서드 테스트
 * 
 * Why: 잔액 조회 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(BalanceController.class)
@DisplayName("잔액 조회 컨트롤러 API")
class GetBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private BalanceService balanceService;

    @Test
    @DisplayName("고객이 자신의 잔액 정보를 성공적으로 조회한다")
    void getBalance_Success() throws Exception {
        // given - 고객이 마이페이지에서 잔액을 확인하는 상황
        Long customerId = 1L;
        Balance customerBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(balanceService.getBalance(customerId)).thenReturn(customerBalance);

        // when & then
        mockMvc.perform(get("/api/balance/{userId}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.amount").value(100000));
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L, "음수 사용자 ID"),
                Arguments.of(0L, "0인 사용자 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("유효하지 않은 사용자 ID로 잔액 조회 시 validation 에러가 발생한다")
    void getBalance_InvalidUserId_ValidationError(Long invalidUserId, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/balance/{userId}", invalidUserId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 잔액 조회 시 예외가 발생한다")
    void getBalance_UserNotFound() throws Exception {
        // given - 존재하지 않는 사용자
        Long newCustomerId = 999L;
        
        when(balanceService.getBalance(newCustomerId)).thenThrow(new UserException.NotFound());

        // when & then
        mockMvc.perform(get("/api/balance/{userId}", newCustomerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("문자열 사용자 ID로 잔액 조회 시 에러가 발생한다")
    void getBalance_StringUserId_Error() throws Exception {
        // when & then
        mockMvc.perform(get("/api/balance/{userId}", "invalid"))
                .andExpect(status().is4xxClientError());
    }
}