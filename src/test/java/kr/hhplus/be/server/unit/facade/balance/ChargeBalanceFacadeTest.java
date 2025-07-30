package kr.hhplus.be.server.unit.facade.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.balance.ChargeBalanceFacade;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private ChargeBalanceFacade chargeBalanceFacade;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceFacade = new ChargeBalanceFacade(chargeBalanceUseCase, lockingPort, userRepositoryPort);
        
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
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(testUser, chargeAmount)).thenReturn(testBalance);
            
            // when
            Balance result = chargeBalanceFacade.chargeBalance(userId, chargeAmount);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
            
            verify(userRepositoryPort).findById(userId);
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(chargeBalanceUseCase).execute(testUser, chargeAmount);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void chargeBalance_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, chargeAmount))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            verify(userRepositoryPort).findById(userId);
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
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(testUser, chargeAmount))
                .thenThrow(new BalanceException.InvalidAmount());
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, chargeAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class);
                
            verify(userRepositoryPort).findById(userId);
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(chargeBalanceUseCase).execute(testUser, chargeAmount);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 잘못된 충전 금액")
        void chargeBalance_InvalidAmount() {
            // given
            Long userId = 1L;
            BigDecimal invalidAmount = new BigDecimal("-10000");
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(chargeBalanceUseCase.execute(testUser, invalidAmount))
                .thenThrow(new BalanceException.InvalidAmount());
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, invalidAmount))
                .isInstanceOf(BalanceException.InvalidAmount.class);
                
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 사용자를 찾을 수 없음")
        void chargeBalance_UserNotFound() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
            
            // when & then
            assertThatThrownBy(() -> chargeBalanceFacade.chargeBalance(userId, chargeAmount))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(userRepositoryPort).findById(userId);
            verify(lockingPort, never()).acquireLock(any());
            verify(chargeBalanceUseCase, never()).execute(any(), any());
            verify(lockingPort, never()).releaseLock(any());
        }
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동시 충전 요청 시 락으로 인한 순차 처리")
        void chargeBalance_ConcurrentRequests_SequentialProcessing() throws InterruptedException {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("10000");
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger lockFailureCount = new AtomicInteger(0);
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            
            // 첫 번째 스레드만 락 획득 성공, 나머지는 실패하도록 설정
            when(lockingPort.acquireLock("balance-" + userId))
                .thenReturn(true)  // 첫 번째 호출만 성공
                .thenReturn(false, false, false, false);  // 나머지는 실패
                
            when(chargeBalanceUseCase.execute(testUser, chargeAmount)).thenReturn(testBalance);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        chargeBalanceFacade.chargeBalance(userId, chargeAmount);
                        successCount.incrementAndGet();
                    } catch (CommonException.ConcurrencyConflict e) {
                        lockFailureCount.incrementAndGet();
                    } catch (Exception e) {
                        // 다른 예외는 무시
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown(); // 모든 스레드 동시 시작
            endLatch.await(); // 모든 스레드 완료 대기
            executor.shutdown();
            
            // then
            assertThat(successCount.get()).isEqualTo(1); // 하나만 성공
            assertThat(lockFailureCount.get()).isEqualTo(4); // 나머지는 락 실패
            
            // 락 획득은 5번 시도되어야 함
            verify(lockingPort, times(5)).acquireLock("balance-" + userId);
            // UseCase는 성공한 1번만 실행되어야 함
            verify(chargeBalanceUseCase, times(1)).execute(testUser, chargeAmount);
            // 락 해제는 성공한 1번만 호출되어야 함
            verify(lockingPort, times(1)).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("서로 다른 사용자의 동시 충전 요청은 독립적으로 처리")
        void chargeBalance_DifferentUsers_IndependentProcessing() throws InterruptedException {
            // given
            Long userId1 = 1L;
            Long userId2 = 2L;
            BigDecimal chargeAmount = new BigDecimal("10000");
            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            User testUser2 = User.builder()
                .id(2L)
                .name("Test User 2")
                .build();
                
            Balance testBalance2 = Balance.builder()
                .id(2L)
                .user(testUser2)
                .amount(new BigDecimal("200000"))
                .updatedAt(LocalDateTime.now())
                .build();
            
            when(userRepositoryPort.findById(userId1)).thenReturn(Optional.of(testUser));
            when(userRepositoryPort.findById(userId2)).thenReturn(Optional.of(testUser2));
            
            // 각각 다른 락이므로 모두 성공해야 함
            when(lockingPort.acquireLock("balance-" + userId1)).thenReturn(true);
            when(lockingPort.acquireLock("balance-" + userId2)).thenReturn(true);
            
            when(chargeBalanceUseCase.execute(testUser, chargeAmount)).thenReturn(testBalance);
            when(chargeBalanceUseCase.execute(testUser2, chargeAmount)).thenReturn(testBalance2);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // when
            executor.submit(() -> {
                try {
                    startLatch.await();
                    chargeBalanceFacade.chargeBalance(userId1, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생하면 안됨
                } finally {
                    endLatch.countDown();
                }
            });
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    chargeBalanceFacade.chargeBalance(userId2, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생하면 안됨
                } finally {
                    endLatch.countDown();
                }
            });
            
            startLatch.countDown(); // 모든 스레드 동시 시작
            endLatch.await(); // 모든 스레드 완료 대기
            executor.shutdown();
            
            // then
            assertThat(successCount.get()).isEqualTo(2); // 둘 다 성공해야 함
            
            // 각각의 락이 획득되어야 함
            verify(lockingPort).acquireLock("balance-" + userId1);
            verify(lockingPort).acquireLock("balance-" + userId2);
            
            // 각각의 UseCase가 실행되어야 함
            verify(chargeBalanceUseCase).execute(testUser, chargeAmount);
            verify(chargeBalanceUseCase).execute(testUser2, chargeAmount);
            
            // 각각의 락이 해제되어야 함
            verify(lockingPort).releaseLock("balance-" + userId1);
            verify(lockingPort).releaseLock("balance-" + userId2);
        }
    }
}