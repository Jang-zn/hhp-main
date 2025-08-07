package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.facade.balance.ChargeBalanceFacade;
import kr.hhplus.be.server.domain.facade.balance.GetBalanceFacade;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * BalanceController 비즈니스 시나리오 테스트
 * 
 * Why: 잔액 컨트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하는지 검증
 * How: 고객의 잔액 충전 및 조회 시나리오를 반영한 컨트롤러 레이어 테스트로 구성
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("잔액 컨트롤러 API 비즈니스 시나리오")
class BalanceControllerTest {

    private BalanceController balanceController;
    
    @Mock
    private ChargeBalanceFacade chargeBalanceFacade;
    
    @Mock
    private GetBalanceFacade getBalanceFacade;

    @BeforeEach
    void setUp() {
        balanceController = new BalanceController(chargeBalanceFacade, getBalanceFacade);
    }

    @Test
    @DisplayName("고객이 자신의 계정에 잔액을 성공적으로 충전한다")
    void chargeBalance_Success() {
        // given - 고객이 마이페이지에서 잔액을 충전하는 상황
        Long customerId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest chargeRequest = new BalanceRequest(customerId, chargeAmount);

        Balance chargedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("150000")) // 기존 100000 + 충전 50000
                .build();
        
        when(chargeBalanceFacade.chargeBalance(customerId, chargeAmount)).thenReturn(chargedBalance);

        // when
        BalanceResponse response = balanceController.chargeBalance(chargeRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(customerId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("150000"));
        assertThat(response.updatedAt()).isNotNull();
    }

    static Stream<Arguments> provideChargeAmounts() {
        return Stream.of(
                Arguments.of(1L, "10000"),
                Arguments.of(2L, "50000"),
                Arguments.of(3L, "100000")
        );
    }

    @ParameterizedTest
    @MethodSource("provideChargeAmounts")
    @DisplayName("다양한 금액으로 잔액 충전이 성공한다")
    void chargeBalance_WithDifferentAmounts(Long customerId, String chargeAmount) {
        // given - 고객이 다양한 금액으로 충전하는 상황
        BalanceRequest chargeRequest = new BalanceRequest(customerId, new BigDecimal(chargeAmount));
        Balance chargedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal(chargeAmount))
                .build();
        
        when(chargeBalanceFacade.chargeBalance(customerId, new BigDecimal(chargeAmount))).thenReturn(chargedBalance);

        // when
        BalanceResponse response = balanceController.chargeBalance(chargeRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(customerId);
        assertThat(response.amount()).isEqualTo(new BigDecimal(chargeAmount));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 잔액 충전 시 예외가 발생한다")
    void chargeBalance_UserNotFound() {
        // given - 탈퇴했거나 존재하지 않는 사용자의 충전 시도
        Long invalidUserId = 999L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest chargeRequest = new BalanceRequest(invalidUserId, chargeAmount);

        when(chargeBalanceFacade.chargeBalance(invalidUserId, chargeAmount))
                .thenThrow(new UserException.InvalidUser());

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(chargeRequest))
                .isInstanceOf(UserException.InvalidUser.class);
    }

    @Test
    @DisplayName("음수 금액으로 잔액 충전 시 예외가 발생한다")
    void chargeBalance_InvalidAmount() {
        // given - 잘못된 충전 금액
        Long customerId = 1L;
        BigDecimal invalidAmount = new BigDecimal("-10000");
        BalanceRequest chargeRequest = new BalanceRequest(customerId, invalidAmount);

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(chargeRequest))
                .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    @Test
    @DisplayName("잘못된 충전 요청 데이터로 충전 시 예외가 발생한다")
    void chargeBalance_WithNullFields() {
        // given - 필수 필드가 누락된 충전 요청
        BalanceRequest invalidRequest = new BalanceRequest(null, null);

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(invalidRequest))
                .isInstanceOf(UserException.InvalidUser.class);
    }

    @Test
    @DisplayName("null 요청으로 잔액 충전 시 예외가 발생한다")
    void chargeBalance_WithNullRequest() {
        // given - 잘못된 API 요청
        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(null))
                .isInstanceOf(CommonException.InvalidRequest.class);
    }

    @Test
    @DisplayName("고객이 자신의 잔액 정보를 성공적으로 조회한다")
    void getBalance_Success() {
        // given - 고객이 마이페이지에서 잔액을 확인하는 상황
        Long customerId = 1L;
        Balance customerBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(getBalanceFacade.getBalance(customerId)).thenReturn(Optional.of(customerBalance));

        // when
        BalanceResponse response = balanceController.getBalance(customerId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(customerId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("100000"));
        assertThat(response.updatedAt()).isNotNull();
    }

    static Stream<Arguments> provideUserIds() {
        return Stream.of(
                Arguments.of(1L),
                Arguments.of(100L),
                Arguments.of(999L)
        );
    }

    @ParameterizedTest
    @MethodSource("provideUserIds")
    @DisplayName("다양한 고객의 잔액 조회가 성공한다")
    void getBalance_WithDifferentUserIds(Long customerId) {
        // given - 다양한 고객들의 잔액 조회 상황
        Balance customerBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("50000"))
                .build();
        
        when(getBalanceFacade.getBalance(customerId)).thenReturn(Optional.of(customerBalance));
        
        // when
        BalanceResponse response = balanceController.getBalance(customerId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(customerId);
        assertThat(response.amount()).isNotNull();
    }

    @Test
    @DisplayName("잔액 정보가 없는 신규 고객의 조회 시 예외가 발생한다")
    void getBalance_UserNotFound() {
        // given - 아직 잔액이 설정되지 않은 신규 고객
        Long newCustomerId = 999L;
        
        when(getBalanceFacade.getBalance(newCustomerId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(newCustomerId))
                .isInstanceOf(UserException.InvalidUser.class);
    }

    @Test
    @DisplayName("null 사용자 ID로 잔액 조회 시 예외가 발생한다")
    void getBalance_WithNullUserId() {
        // given - 잘못된 사용자 ID
        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(null))
                .isInstanceOf(UserException.InvalidUser.class);
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("유효하지 않은 사용자 ID로 잔액 조회 시 예외가 발생한다")
    void getBalance_WithInvalidUserIds(Long invalidUserId) {
        // given - 유효하지 않은 사용자 ID
        when(getBalanceFacade.getBalance(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(invalidUserId))
                .isInstanceOf(UserException.InvalidUser.class);
    }
}