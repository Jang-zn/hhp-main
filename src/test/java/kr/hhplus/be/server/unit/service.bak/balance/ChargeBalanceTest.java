package kr.hhplus.be.server.unit.service.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.service.BalanceService;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BalanceService.chargeBalance 메서드 테스트
 * 
 * Why: 잔액 충전 서비스의 비즈니스 로직이 요구사항을 충족하는지 검증
 * How: 잔액 충전 시나리오를 반영한 서비스 레이어 테스트로 구성
 */
@DisplayName("잔액 충전 서비스")
class ChargeBalanceTest {

    @Mock
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private BalanceService balanceService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        balanceService = new BalanceService(chargeBalanceUseCase, null, lockingPort, userRepositoryPort);
    }

    @Test
    @DisplayName("정상적인 잔액 충전이 성공한다")
    void chargeBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        Balance expectedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId)
                .amount(new BigDecimal("150000"))
                .build();
                
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
        when(chargeBalanceUseCase.execute(userId, chargeAmount)).thenReturn(expectedBalance);
        
        // when
        Balance result = balanceService.chargeBalance(userId, chargeAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
        
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock("balance-" + userId);
        verify(chargeBalanceUseCase).execute(userId, chargeAmount);
        verify(lockingPort).releaseLock("balance-" + userId);
    }
        
    @Test
    @DisplayName("락 획득 실패 시 동시성 충돌 예외가 발생한다")
    void chargeBalance_LockAcquisitionFailed() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock("balance-" + userId);
        verify(chargeBalanceUseCase, never()).execute(any(), any());
        verify(lockingPort, never()).releaseLock(any());
    }
        
    @Test
    @DisplayName("UseCase 실행 중 예외 발생 시 락이 해제된다")
    void chargeBalance_UseCaseException_ReleaseLock() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
        when(chargeBalanceUseCase.execute(userId, chargeAmount))
            .thenThrow(new BalanceException.InvalidAmount());
        
        // when & then
        assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock("balance-" + userId);
        verify(chargeBalanceUseCase).execute(userId, chargeAmount);
        verify(lockingPort).releaseLock("balance-" + userId);
    }
        
    @Test
    @DisplayName("잘못된 충전 금액으로 요청 시 예외가 발생한다")
    void chargeBalance_InvalidAmount() {
        // given
        Long userId = 1L;
        BigDecimal invalidAmount = new BigDecimal("-10000");
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
        when(chargeBalanceUseCase.execute(userId, invalidAmount))
            .thenThrow(new BalanceException.InvalidAmount());
        
        // when & then
        assertThatThrownBy(() -> balanceService.chargeBalance(userId, invalidAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(lockingPort).releaseLock("balance-" + userId);
    }
      
    @Test
    @DisplayName("존재하지 않는 사용자로 요청 시 예외가 발생한다")
    void chargeBalance_UserNotFound() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.existsById(userId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> balanceService.chargeBalance(userId, chargeAmount))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort, never()).acquireLock(any());
        verify(chargeBalanceUseCase, never()).execute(any(), any());
        verify(lockingPort, never()).releaseLock(any());
    }
    
    @Test
    @DisplayName("동시 충전 요청 시 락으로 인한 순차 처리가 보장된다")
    void chargeBalance_ConcurrentRequests_SequentialProcessing() throws InterruptedException {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("10000");
        int threadCount = 5;
        Balance expectedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId)
                .build();
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        // 첫 번째 스레드만 락 획득 성공, 나머지는 실패하도록 설정
        when(lockingPort.acquireLock("balance-" + userId))
            .thenReturn(true)  // 첫 번째 호출만 성공
            .thenReturn(false, false, false, false);  // 나머지는 실패
            
        when(chargeBalanceUseCase.execute(userId, chargeAmount)).thenReturn(expectedBalance);
        
        // when & then
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
            threadCount,
            () -> {
                try {
                    balanceService.chargeBalance(userId, chargeAmount);
                    return "SUCCESS";
                } catch (CommonException.ConcurrencyConflict e) {
                    throw new RuntimeException("LOCK_FAILED");
                } catch (Exception e) {
                    throw new RuntimeException("OTHER_ERROR");
                }
            }
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(1); // 하나만 성공
        assertThat(result.getFailureCount()).isEqualTo(4); // 나머지는 락 실패
        
        verify(lockingPort, times(5)).acquireLock("balance-" + userId);
        verify(chargeBalanceUseCase, times(1)).execute(userId, chargeAmount);
        verify(lockingPort, times(1)).releaseLock("balance-" + userId);
    }
        
    @Test
    @DisplayName("서로 다른 사용자의 동시 충전 요청은 독립적으로 처리된다")
    void chargeBalance_DifferentUsers_IndependentProcessing() throws InterruptedException {
        // given
        Long userId1 = 1L;
        Long userId2 = 2L;
        BigDecimal chargeAmount = new BigDecimal("10000");
        
        Balance testBalance1 = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId1)
                .build();
        Balance testBalance2 = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId2)
                .amount(new BigDecimal("200000"))
                .build();
        
        when(userRepositoryPort.existsById(userId1)).thenReturn(true);
        when(userRepositoryPort.existsById(userId2)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId1)).thenReturn(true);
        when(lockingPort.acquireLock("balance-" + userId2)).thenReturn(true);
        when(chargeBalanceUseCase.execute(userId1, chargeAmount)).thenReturn(testBalance1);
        when(chargeBalanceUseCase.execute(userId2, chargeAmount)).thenReturn(testBalance2);
        
        // when & then
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
            List.of(
                () -> {
                    try {
                        balanceService.chargeBalance(userId1, chargeAmount);
                    } catch (Exception e) {
                        throw new RuntimeException("USER1_FAILED: " + e.getMessage());
                    }
                },
                () -> {
                    try {
                        balanceService.chargeBalance(userId2, chargeAmount);
                    } catch (Exception e) {
                        throw new RuntimeException("USER2_FAILED: " + e.getMessage());
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(2); // 둘 다 성공해야 함
        
        verify(lockingPort).acquireLock("balance-" + userId1);
        verify(lockingPort).acquireLock("balance-" + userId2);
        verify(chargeBalanceUseCase).execute(userId1, chargeAmount);
        verify(chargeBalanceUseCase).execute(userId2, chargeAmount);
        verify(lockingPort).releaseLock("balance-" + userId1);
        verify(lockingPort).releaseLock("balance-" + userId2);
    }
}