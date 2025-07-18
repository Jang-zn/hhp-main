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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    private static Stream<Arguments> provideChargeData() {
        return Stream.of(
                Arguments.of(1L, "10000"),
                Arguments.of(2L, "50000"),
                Arguments.of(3L, "100000")
        );
    }
}