package kr.hhplus.be.server.unit.facade.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
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

/**
 * IssueCouponFacade 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 발급 파사드의 동시성 제어와 비즈니스 규칙이 요구사항을 충족하는지 검증
 * How: 쿠폰 발급 시나리오를 반영한 파사드 레이어 테스트로 구성
 */
@DisplayName("쿠폰 발급 파사드 비즈니스 시나리오")
class IssueCouponFacadeTest {

    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    private IssueCouponFacade issueCouponFacade;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        issueCouponFacade = new IssueCouponFacade(issueCouponUseCase, lockingPort);
    }

    @Test
    @DisplayName("정상적인 쿠폰 발급이 성공한다")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        CouponHistory expectedHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .userId(userId)
                .couponId(couponId)
                .build();
        
        when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
        when(issueCouponUseCase.execute(userId, couponId)).thenReturn(expectedHistory);
        
        // when
        CouponHistory result = issueCouponFacade.issueCoupon(userId, couponId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedHistory);
        
        verify(lockingPort).acquireLock("coupon-" + couponId);
        verify(issueCouponUseCase).execute(userId, couponId);
        verify(lockingPort).releaseLock("coupon-" + couponId);
    }
        
    @Test
    @DisplayName("락 획득 실패 시 동시성 충돌 예외가 발생한다")
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
    @DisplayName("UseCase 실행 중 예외 발생 시 락이 해제된다")
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
    
    @Test
    @DisplayName("동일 쿠폰에 대한 동시 발급 요청 시 락으로 인한 순차 처리가 보장된다")
    void issueCoupon_ConcurrentSameCouponRequests_SequentialProcessing() throws InterruptedException {
        // given
        Long couponId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;
        CouponHistory expectedHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .couponId(couponId)
                .build();
        
        // 첫 번째 스레드만 락 획득 성공
        when(lockingPort.acquireLock("coupon-" + couponId))
            .thenReturn(true)  // 첫 번째 호출만 성공
            .thenReturn(false); // 두 번째는 실패
            
        when(issueCouponUseCase.execute(anyLong(), eq(couponId))).thenReturn(expectedHistory);
        
        // when & then
        ConcurrencyTestHelper.ConcurrentResult result = ConcurrencyTestHelper.executeConcurrentTasks(
            List.of(
                () -> {
                    try {
                        issueCouponFacade.issueCoupon(userId1, couponId);
                        return ConcurrencyTestHelper.TaskResult.success();
                    } catch (CommonException.ConcurrencyConflict e) {
                        return ConcurrencyTestHelper.TaskResult.failure("LOCK_FAILED");
                    } catch (Exception e) {
                        return ConcurrencyTestHelper.TaskResult.failure("OTHER_ERROR");
                    }
                },
                () -> {
                    try {
                        issueCouponFacade.issueCoupon(userId2, couponId);
                        return ConcurrencyTestHelper.TaskResult.success();
                    } catch (CommonException.ConcurrencyConflict e) {
                        return ConcurrencyTestHelper.TaskResult.failure("LOCK_FAILED");
                    } catch (Exception e) {
                        return ConcurrencyTestHelper.TaskResult.failure("OTHER_ERROR");
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(1); // 하나만 성공
        assertThat(result.getFailureCount("LOCK_FAILED")).isEqualTo(1); // 하나는 락 실패
        
        verify(lockingPort, times(2)).acquireLock("coupon-" + couponId);
        verify(issueCouponUseCase, times(1)).execute(anyLong(), eq(couponId));
        verify(lockingPort, times(1)).releaseLock("coupon-" + couponId);
    }
        
    @Test
    @DisplayName("서로 다른 쿠폰에 대한 동시 발급 요청은 독립적으로 처리된다")
    void issueCoupon_DifferentCouponRequests_IndependentProcessing() throws InterruptedException {
        // given
        Long couponId1 = 1L;
        Long couponId2 = 2L;
        Long userId1 = 1L;
        Long userId2 = 2L;
        
        CouponHistory expectedHistory1 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .userId(userId1)
                .couponId(couponId1)
                .build();
        CouponHistory expectedHistory2 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .userId(userId2)
                .couponId(couponId2)
                .build();
        
        when(lockingPort.acquireLock("coupon-" + couponId1)).thenReturn(true);
        when(lockingPort.acquireLock("coupon-" + couponId2)).thenReturn(true);
        when(issueCouponUseCase.execute(userId1, couponId1)).thenReturn(expectedHistory1);
        when(issueCouponUseCase.execute(userId2, couponId2)).thenReturn(expectedHistory2);
        
        // when & then
        ConcurrencyTestHelper.ConcurrentResult result = ConcurrencyTestHelper.executeConcurrentTasks(
            List.of(
                () -> {
                    try {
                        issueCouponFacade.issueCoupon(userId1, couponId1);
                        return ConcurrencyTestHelper.TaskResult.success();
                    } catch (Exception e) {
                        return ConcurrencyTestHelper.TaskResult.failure("COUPON1_FAILED");
                    }
                },
                () -> {
                    try {
                        issueCouponFacade.issueCoupon(userId2, couponId2);
                        return ConcurrencyTestHelper.TaskResult.success();
                    } catch (Exception e) {
                        return ConcurrencyTestHelper.TaskResult.failure("COUPON2_FAILED");
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(2); // 둘 다 성공해야 함
        
        verify(lockingPort).acquireLock("coupon-" + couponId1);
        verify(lockingPort).acquireLock("coupon-" + couponId2);
        verify(issueCouponUseCase).execute(userId1, couponId1);
        verify(issueCouponUseCase).execute(userId2, couponId2);
        verify(lockingPort).releaseLock("coupon-" + couponId1);
        verify(lockingPort).releaseLock("coupon-" + couponId2);
    }
}