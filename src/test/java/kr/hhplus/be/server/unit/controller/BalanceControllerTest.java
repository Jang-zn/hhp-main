package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.exception.BalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@DisplayName("BalanceController 단위 테스트")
class BalanceControllerTest {

    private BalanceController balanceController;
    
    @Mock
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    @Mock
    private GetBalanceUseCase getBalanceUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        balanceController = new BalanceController(chargeBalanceUseCase, getBalanceUseCase);
    }

    @Test
    @DisplayName("잔액 충전 API 성공")
    void chargeBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest request = new BalanceRequest(userId, chargeAmount);
        
        User user = User.builder().name("테스트 사용자").build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("150000"))
                .build();
        
        when(chargeBalanceUseCase.execute(userId, chargeAmount)).thenReturn(balance);

        // when
        BalanceResponse response = balanceController.chargeBalance(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("150000"));
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("잔액 조회 API 성공")
    void getBalance_Success() {
        // given
        Long userId = 1L;
        
        User user = User.builder().name("테스트 사용자").build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(getBalanceUseCase.execute(userId)).thenReturn(Optional.of(balance));

        // when
        BalanceResponse response = balanceController.getBalance(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("100000"));
        assertThat(response.updatedAt()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("provideChargeData")
    @DisplayName("다양한 충전 금액으로 잔액 충전")
    void chargeBalance_WithDifferentAmounts(Long userId, String chargeAmount) {
        // given
        BalanceRequest request = new BalanceRequest(userId, new BigDecimal(chargeAmount));
        
        User user = User.builder().name("테스트 사용자").build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal(chargeAmount))
                .build();
        
        when(chargeBalanceUseCase.execute(userId, new BigDecimal(chargeAmount))).thenReturn(balance);

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
        // given
        User user = User.builder().name("테스트 사용자").build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("50000"))
                .build();
        
        when(getBalanceUseCase.execute(userId)).thenReturn(Optional.of(balance));
        
        // when
        BalanceResponse response = balanceController.getBalance(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 잔액 충전 시 예외 발생")
    void chargeBalance_UserNotFound() {
        // given
        Long userId = 999L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest request = new BalanceRequest(userId, chargeAmount);
        
        when(chargeBalanceUseCase.execute(userId, chargeAmount))
                .thenThrow(new BalanceException.InvalidUser());

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(request))
                .isInstanceOf(BalanceException.InvalidUser.class)
                .hasMessage("Invalid user ID");
    }

    @Test
    @DisplayName("비정상 충전 금액으로 인한 예외 발생")
    void chargeBalance_InvalidAmount() {
        // given
        Long userId = 1L;
        BigDecimal invalidAmount = new BigDecimal("-10000");
        BalanceRequest request = new BalanceRequest(userId, invalidAmount);
        
        when(chargeBalanceUseCase.execute(userId, invalidAmount))
                .thenThrow(new BalanceException.InvalidAmount());

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(request))
                .isInstanceOf(BalanceException.InvalidAmount.class)
                .hasMessage("Amount must be between 1,000 and 1,000,000");
    }

    @Test
    @DisplayName("동시성 충돌로 인한 예외 발생")
    void chargeBalance_ConcurrencyConflict() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BalanceRequest request = new BalanceRequest(userId, chargeAmount);
        
        when(chargeBalanceUseCase.execute(userId, chargeAmount))
                .thenThrow(new BalanceException.ConcurrencyConflict());

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(request))
                .isInstanceOf(BalanceException.ConcurrencyConflict.class)
                .hasMessage("Concurrent balance update conflict");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 잔액 조회")
    void getBalance_UserNotFound() {
        // given
        Long userId = 999L;
        
        when(getBalanceUseCase.execute(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(userId))
                .isInstanceOf(BalanceException.InvalidUser.class)
                .hasMessage("Invalid user ID");
    }

    @Test
    @DisplayName("null 사용자 ID로 잔액 조회")
    void getBalance_WithNullUserId() {
        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 요청으로 잔액 충전")
    void chargeBalance_WithNullRequest() {
        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidChargeData")
    @DisplayName("비정상 충전 데이터로 예외 발생")
    void chargeBalance_WithInvalidData(String description, Long userId, String amount, Class<? extends Exception> expectedException) {
        // given
        BalanceRequest request = new BalanceRequest(userId, new BigDecimal(amount));
        
        when(chargeBalanceUseCase.execute(userId, new BigDecimal(amount)))
                .thenThrow(expectedException);

        // when & then
        assertThatThrownBy(() -> balanceController.chargeBalance(request))
                .isInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("비정상 사용자 ID로 잔액 조회")
    void getBalance_WithInvalidUserIds(Long invalidUserId) {
        // given
        when(getBalanceUseCase.execute(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceController.getBalance(invalidUserId))
                .isInstanceOf(BalanceException.InvalidUser.class);
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

    private static Stream<Arguments> provideInvalidChargeData() {
        return Stream.of(
                Arguments.of("음수 금액", 1L, "-10000", BalanceException.InvalidAmount.class),
                Arguments.of("최소 금액 미만", 1L, "500", BalanceException.InvalidAmount.class),
                Arguments.of("최대 금액 초과", 1L, "2000000", BalanceException.InvalidAmount.class)
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE)
        );
    }
}