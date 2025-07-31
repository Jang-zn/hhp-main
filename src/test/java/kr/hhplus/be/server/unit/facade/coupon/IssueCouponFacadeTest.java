package kr.hhplus.be.server.unit.facade.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("IssueCouponFacade 단위 테스트")
class IssueCouponFacadeTest {

    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    private IssueCouponFacade issueCouponFacade;
    
    private CouponHistory testCouponHistory;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        issueCouponFacade = new IssueCouponFacade(issueCouponUseCase, lockingPort);
        
        testCouponHistory = mock(CouponHistory.class);
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCoupon {
        
        @Test
        @DisplayName("성공 - 정상 쿠폰 발급")
        void issueCoupon_Success() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId)).thenReturn(testCouponHistory);
            
            // when
            CouponHistory result = issueCouponFacade.issueCoupon(userId, couponId);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(testCouponHistory);
            
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase).execute(userId, couponId);
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void issueCoupon_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase, never()).execute(any(), any());
            verify(lockingPort, never()).releaseLock(any());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생 시 락 해제")
        void issueCoupon_UseCaseException_ReleaseLock() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId))
                .thenThrow(new CouponException.NotFound());
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class);
                
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase).execute(userId, couponId);
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동일 쿠폰에 대한 동시 발급 요청 시 락으로 인한 순차 처리")
        void issueCoupon_ConcurrentSameCouponRequests_SequentialProcessing() throws InterruptedException {
            // given
            Long couponId = 1L;
            Long userId1 = 1L;
            Long userId2 = 2L;
            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger lockFailureCount = new AtomicInteger(0);
            
            // 첫 번째 스레드만 락 획득 성공
            when(lockingPort.acquireLock("coupon-" + couponId))
                .thenReturn(true)  // 첫 번째 호출만 성공
                .thenReturn(false); // 두 번째는 실패
                
            when(issueCouponUseCase.execute(userId1, couponId)).thenReturn(testCouponHistory);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // when
            executor.submit(() -> {
                try {
                    startLatch.await();
                    issueCouponFacade.issueCoupon(userId1, couponId);
                    successCount.incrementAndGet();
                } catch (CommonException.ConcurrencyConflict e) {
                    lockFailureCount.incrementAndGet();
                } catch (Exception e) {
                    // 다른 예외는 무시
                } finally {
                    endLatch.countDown();
                }
            });
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    issueCouponFacade.issueCoupon(userId2, couponId);
                    successCount.incrementAndGet();
                } catch (CommonException.ConcurrencyConflict e) {
                    lockFailureCount.incrementAndGet();
                } catch (Exception e) {
                    // 다른 예외는 무시
                } finally {
                    endLatch.countDown();
                }
            });
            
            startLatch.countDown(); // 모든 스레드 동시 시작
            endLatch.await(); // 모든 스레드 완료 대기
            executor.shutdown();
            
            // then
            assertThat(successCount.get()).isEqualTo(1); // 하나만 성공
            assertThat(lockFailureCount.get()).isEqualTo(1); // 하나는 락 실패
            
            // 락 획득은 2번 시도되어야 함
            verify(lockingPort, times(2)).acquireLock("coupon-" + couponId);
            // UseCase는 성공한 1번만 실행되어야 함 (어떤 userId든 상관없이)
            verify(issueCouponUseCase, times(1)).execute(anyLong(), eq(couponId));
            // 락 해제는 성공한 1번만 호출되어야 함
            verify(lockingPort, times(1)).releaseLock("coupon-" + couponId);
        }
        
        @Test
        @DisplayName("서로 다른 쿠폰에 대한 동시 발급 요청은 독립적으로 처리")
        void issueCoupon_DifferentCouponRequests_IndependentProcessing() throws InterruptedException {
            // given
            Long couponId1 = 1L;
            Long couponId2 = 2L;
            Long userId1 = 1L;
            Long userId2 = 2L;
            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            CouponHistory testCouponHistory2 = mock(CouponHistory.class);
            
            // 각각 다른 락이므로 모두 성공해야 함
            when(lockingPort.acquireLock("coupon-" + couponId1)).thenReturn(true);
            when(lockingPort.acquireLock("coupon-" + couponId2)).thenReturn(true);
            
            when(issueCouponUseCase.execute(userId1, couponId1)).thenReturn(testCouponHistory);
            when(issueCouponUseCase.execute(userId2, couponId2)).thenReturn(testCouponHistory2);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // when
            executor.submit(() -> {
                try {
                    startLatch.await();
                    issueCouponFacade.issueCoupon(userId1, couponId1);
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
                    issueCouponFacade.issueCoupon(userId2, couponId2);
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
            verify(lockingPort).acquireLock("coupon-" + couponId1);
            verify(lockingPort).acquireLock("coupon-" + couponId2);
            
            // 각각의 UseCase가 실행되어야 함
            verify(issueCouponUseCase).execute(userId1, couponId1);
            verify(issueCouponUseCase).execute(userId2, couponId2);
            
            // 각각의 락이 해제되어야 함
            verify(lockingPort).releaseLock("coupon-" + couponId1);
            verify(lockingPort).releaseLock("coupon-" + couponId2);
        }
    }
}