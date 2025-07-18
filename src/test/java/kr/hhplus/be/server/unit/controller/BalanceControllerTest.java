package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.api.dto.request.BalanceChargeRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BalanceController 단위 테스트")
class BalanceControllerTest {

    private BalanceController balanceController;
    private ChargeBalanceUseCase chargeBalanceUseCase;
    private GetBalanceUseCase getBalanceUseCase;

    @BeforeEach
    void setUp() {
        chargeBalanceUseCase = new ChargeBalanceUseCase(null, null, null, null);
        getBalanceUseCase = new GetBalanceUseCase(null, null, null);
        balanceController = new BalanceController(chargeBalanceUseCase, getBalanceUseCase);
    }

    @Test
    @DisplayName("잔액 충전 API 성공")
    void chargeBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceChargeRequest request = new BalanceChargeRequest(userId, chargeAmount);

        // when
        BalanceResponse response = balanceController.chargeBalance(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(chargeAmount);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("잔액 조회 API 성공")
    void getBalance_Success() {
        // given
        Long userId = 1L;

        // when
        BalanceResponse response = balanceController.getBalance(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("50000"));
        assertThat(response.updatedAt()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("provideChargeData")
    @DisplayName("다양한 충전 금액으로 잔액 충전")
    void chargeBalance_WithDifferentAmounts(Long userId, String chargeAmount) {
        // given
        BalanceChargeRequest request = new BalanceChargeRequest(userId, new BigDecimal(chargeAmount));

        // when
        BalanceResponse response = balanceController.chargeBalance(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(new BigDecimal(chargeAmount));
    }

    @ParameterizedTest
    @MethodSource("provideUserIds")
    @DisplayName("다양한 사용자 ID로 잔액 조회")
    void getBalance_WithDifferentUserIds(Long userId) {
        // when
        BalanceResponse response = balanceController.getBalance(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isNotNull();
    }

    private static Stream<Arguments> provideChargeData() {
        return Stream.of(
                Arguments.of(1L, "10000"),
                Arguments.of(2L, "50000"),
                Arguments.of(3L, "100000")
        );
    }

    private static Stream<Arguments> provideUserIds() {
        return Stream.of(
                Arguments.of(1L),
                Arguments.of(100L),
                Arguments.of(999L)
        );
    }
}