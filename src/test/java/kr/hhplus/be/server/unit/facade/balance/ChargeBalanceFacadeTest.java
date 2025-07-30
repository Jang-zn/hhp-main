package kr.hhplus.be.server.unit.facade.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.balance.ChargeBalanceFacade;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChargeBalanceFacade 단위 테스트")
class ChargeBalanceFacadeTest {

    @Mock
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    private ChargeBalanceFacade chargeBalanceFacade;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceFacade = new ChargeBalanceFacade(chargeBalanceUseCase, lockingPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testBalance = Balance.builder()
            .id(1L)
            .user(testUser)
            .amount(new BigDecimal("150000"))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("잔액 충전")
    class ChargeBalance {
        
        @Test
        @DisplayName("성공 - 정상 잔액 충전")
        void chargeBalance_Success() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(userId, chargeAmount)).thenReturn(testBalance);
            
            // when
            Balance result = chargeBalanceFacade.chargeBalance(userId, chargeAmount);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
            
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(chargeBalanceUseCase).execute(userId, chargeAmount);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void chargeBalance_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, chargeAmount))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(chargeBalanceUseCase, never()).execute(any(), any());
            verify(lockingPort, never()).releaseLock(any());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생 시 락 해제")
        void chargeBalance_UseCaseException_ReleaseLock() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(userId, chargeAmount))
                .thenThrow(new UserException.InvalidUser());
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, chargeAmount))
                .isInstanceOf(UserException.InvalidUser.class);
                
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(chargeBalanceUseCase).execute(userId, chargeAmount);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 잘못된 충전 금액")
        void chargeBalance_InvalidAmount() {
            // given
            Long userId = 1L;
            BigDecimal invalidAmount = new BigDecimal("-10000");
            
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(userId, invalidAmount))
                .thenThrow(new BalanceException.InvalidAmount());
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, invalidAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class);
                
            verify(lockingPort).releaseLock("balance-" + userId);
        }
    }
}