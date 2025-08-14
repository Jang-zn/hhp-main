package kr.hhplus.be.server.unit.service.coupon;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponService.issueCoupon 메서드 테스트
 * 
 * Why: 쿠폰 발급 서비스의 동시성 제어와 비즈니스 규칙이 요구사항을 충족하는지 검증
 * How: 쿠폰 발급 시나리오를 반영한 서비스 레이어 테스트로 구성
 */
@DisplayName("쿠폰 발급 서비스")
class IssueCouponTest {

    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private GetCouponListUseCase getCouponListUseCase;
    
    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    private CouponService couponService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        couponService = new CouponService(transactionTemplate, getCouponListUseCase, issueCouponUseCase, lockingPort, userRepositoryPort, cachePort, keyGenerator);
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
        
        String lockKey = "coupon:lock:coupon_1";
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(userId, couponId)).thenReturn(expectedHistory);
        
        // when
        CouponHistory result = couponService.issueCoupon(couponId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedHistory);
        
        verify(keyGenerator).generateCouponKey(couponId);
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock(lockKey);
        verify(transactionTemplate).execute(any());
        verify(issueCouponUseCase).execute(userId, couponId);
        verify(cachePort).evictByPattern("coupon:list:user_1_*");
        verify(lockingPort).releaseLock(lockKey);
    }
        
    @Test
    @DisplayName("락 획득 실패 시 동시성 충돌 예외가 발생한다")
    void issueCoupon_LockAcquisitionFailed() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        String lockKey = "coupon:lock:coupon_1";
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(keyGenerator).generateCouponKey(couponId);
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock(lockKey);
        verify(transactionTemplate, never()).execute(any());
        verify(issueCouponUseCase, never()).execute(any(), any());
        verify(lockingPort, never()).releaseLock(any());
    }
        
    @Test
    @DisplayName("UseCase 실행 중 예외 발생 시 락이 해제된다")
    void issueCoupon_UseCaseException_ReleaseLock() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        String lockKey = "coupon:lock:coupon_1";
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(userId, couponId))
            .thenThrow(new CouponException.NotFound());
        
        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
            .isInstanceOf(CouponException.NotFound.class);
            
        verify(keyGenerator).generateCouponKey(couponId);
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).acquireLock(lockKey);
        verify(transactionTemplate).execute(any());
        verify(issueCouponUseCase).execute(userId, couponId);
        verify(lockingPort).releaseLock(lockKey);
    }
    
    @Test
    @DisplayName("존재하지 않는 사용자로 요청 시 예외가 발생한다")
    void issueCoupon_UserNotFound() {
        // given
        Long userId = 999L;
        Long couponId = 1L;
        
        when(userRepositoryPort.existsById(userId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort, never()).acquireLock(any());
        verify(issueCouponUseCase, never()).execute(any(), any());
        verify(lockingPort, never()).releaseLock(any());
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
        
        String lockKey = "coupon:lock:coupon_1";
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(userRepositoryPort.existsById(userId1)).thenReturn(true);
        when(userRepositoryPort.existsById(userId2)).thenReturn(true);
        // 첫 번째 스레드만 락 획득 성공
        when(lockingPort.acquireLock(lockKey))
            .thenReturn(true)  // 첫 번째 호출만 성공
            .thenReturn(false); // 두 번째는 실패
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(anyLong(), eq(couponId))).thenReturn(expectedHistory);
        
        // when & then
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
            List.of(
                () -> {
                    try {
                        couponService.issueCoupon(couponId, userId1);
                    } catch (CommonException.ConcurrencyConflict e) {
                        throw new RuntimeException("LOCK_FAILED");
                    } catch (Exception e) {
                        throw new RuntimeException("OTHER_ERROR");
                    }
                },
                () -> {
                    try {
                        couponService.issueCoupon(couponId, userId2);
                    } catch (CommonException.ConcurrencyConflict e) {
                        throw new RuntimeException("LOCK_FAILED");
                    } catch (Exception e) {
                        throw new RuntimeException("OTHER_ERROR");
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(1); // 하나만 성공
        assertThat(result.getFailureCount()).isEqualTo(1); // 하나는 락 실패
        
        verify(keyGenerator, atLeast(1)).generateCouponKey(couponId);
        verify(lockingPort, times(2)).acquireLock(lockKey);
        verify(transactionTemplate, times(1)).execute(any());
        verify(issueCouponUseCase, times(1)).execute(anyLong(), eq(couponId));
        verify(lockingPort, times(1)).releaseLock(lockKey);
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
        
        String lockKey1 = "coupon:lock:coupon_1";
        String lockKey2 = "coupon:lock:coupon_2";
        when(keyGenerator.generateCouponKey(couponId1)).thenReturn(lockKey1);
        when(keyGenerator.generateCouponKey(couponId2)).thenReturn(lockKey2);
        when(userRepositoryPort.existsById(userId1)).thenReturn(true);
        when(userRepositoryPort.existsById(userId2)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey1)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey2)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(userId1, couponId1)).thenReturn(expectedHistory1);
        when(issueCouponUseCase.execute(userId2, couponId2)).thenReturn(expectedHistory2);
        
        // when & then
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
            List.of(
                () -> {
                    try {
                        couponService.issueCoupon(couponId1, userId1);
                    } catch (Exception e) {
                        throw new RuntimeException("COUPON1_FAILED: " + e.getMessage());
                    }
                },
                () -> {
                    try {
                        couponService.issueCoupon(couponId2, userId2);
                    } catch (Exception e) {
                        throw new RuntimeException("COUPON2_FAILED: " + e.getMessage());
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(2); // 둘 다 성공해야 함
        
        verify(keyGenerator).generateCouponKey(couponId1);
        verify(keyGenerator).generateCouponKey(couponId2);
        verify(lockingPort).acquireLock(lockKey1);
        verify(lockingPort).acquireLock(lockKey2);
        verify(transactionTemplate, times(2)).execute(any());
        verify(issueCouponUseCase).execute(userId1, couponId1);
        verify(issueCouponUseCase).execute(userId2, couponId2);
        verify(lockingPort).releaseLock(lockKey1);
        verify(lockingPort).releaseLock(lockKey2);
    }
}