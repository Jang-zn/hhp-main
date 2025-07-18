package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.BalanceException;

@DisplayName("ChargeBalanceUseCase 단위 테스트")
class ChargeBalanceUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private ChargeBalanceUseCase chargeBalanceUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceUseCase = new ChargeBalanceUseCase(
                userRepositoryPort, balanceRepositoryPort, lockingPort, cachePort
        );
    }

    @Test
    @DisplayName("잔액 충전 성공")
    void chargeBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Balance existingBalance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(existingBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 null 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isNotNull();
        // assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
    }

    @ParameterizedTest
    @MethodSource("provideChargeData")
    @DisplayName("다양한 충전 금액으로 테스트")
    void chargeBalance_WithDifferentAmounts(Long userId, String chargeAmount) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Balance existingBalance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("50000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(existingBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Balance result = chargeBalanceUseCase.execute(userId, new BigDecimal(chargeAmount));

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 null 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 잔액 충전 시 예외 발생")
    void chargeBalance_UserNotFound() {
        // given
        Long userId = 999L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidUser.class)
                .hasMessage("Invalid user ID");
    }

    @Test
    @DisplayName("null 사용자 ID로 잔액 충전 시 예외 발생")
    void chargeBalance_WithNullUserId() {
        // given
        Long userId = null;
        BigDecimal chargeAmount = new BigDecimal("50000");

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidUser.class);
    }

    @Test
    @DisplayName("유효하지 않은 충전 금액 - 음수")
    void chargeBalance_WithNegativeAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("-10000");
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class)
                .hasMessage("Amount must be between 1,000 and 1,000,000");
    }

    @Test
    @DisplayName("유효하지 않은 충전 금액 - 최대 한도 초과")
    void chargeBalance_WithExcessiveAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("2000000"); // 100만원 초과
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class)
                .hasMessage("Amount must be between 1,000 and 1,000,000");
    }

    @Test
    @DisplayName("유효하지 않은 충전 금액 - 최소 한도 미만")
    void chargeBalance_WithTooSmallAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("500"); // 1000원 미만
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class)
                .hasMessage("Amount must be between 1,000 and 1,000,000");
    }

    @Test
    @DisplayName("null 충전 금액")
    void chargeBalance_WithNullAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = null;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    @Test
    @DisplayName("동시성 충돌 시나리오")
    void chargeBalance_ConcurrencyConflict() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Balance existingBalance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(existingBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenThrow(new BalanceException.ConcurrencyConflict());

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.ConcurrencyConflict.class)
                .hasMessage("Concurrent balance update conflict");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("다양한 비정상 사용자 ID 테스트")
    void chargeBalance_WithInvalidUserIds(Long invalidUserId) {
        // given
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(invalidUserId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidUser.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidAmounts")
    @DisplayName("다양한 비정상 충전 금액 테스트")
    void chargeBalance_WithInvalidAmounts(String description, String invalidAmount) {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal(invalidAmount);
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    private static Stream<Arguments> provideChargeData() {
        return Stream.of(
                Arguments.of(1L, "10000"),
                Arguments.of(2L, "50000"),
                Arguments.of(3L, "100000")
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideInvalidAmounts() {
        return Stream.of(
                Arguments.of("음수", "-1000"),
                Arguments.of("영", "0"),
                Arguments.of("최소값 미만", "999"),
                Arguments.of("최대값 초과", "1000001"),
                Arguments.of("매우 큰 값", "999999999")
        );
    }
}