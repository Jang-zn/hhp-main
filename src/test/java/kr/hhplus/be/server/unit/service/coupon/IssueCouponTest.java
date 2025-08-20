package kr.hhplus.be.server.unit.service.coupon;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponByIdUseCase;
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
    private GetCouponByIdUseCase getCouponByIdUseCase;
    
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
        couponService = new CouponService(transactionTemplate, getCouponListUseCase, issueCouponUseCase, getCouponByIdUseCase, lockingPort, userRepositoryPort, cachePort, keyGenerator);
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
        
        // 쿠폰 정보 mock
        var coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .withQuantity(100, 0)
                .build();
        
        String lockKey = "coupon:lock:coupon_1";
        String couponCounterKey = "coupon:counter:1";
        String couponUserKey = "coupon:user:1:1";
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(getCouponByIdUseCase.execute(couponId)).thenReturn(coupon);
        when(keyGenerator.generateCouponCounterKey(couponId)).thenReturn(couponCounterKey);
        when(keyGenerator.generateCouponUserKey(couponId, userId)).thenReturn(couponUserKey);
        when(cachePort.issueCouponAtomically(couponCounterKey, couponUserKey, 100)).thenReturn(1L);
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(lockingPort.acquireLock(lockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(userId, couponId)).thenReturn(expectedHistory);
        when(keyGenerator.generateCouponListCachePattern(userId)).thenReturn("coupon:list:user_1_*");
        
        // when
        CouponHistory result = couponService.issueCoupon(couponId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedHistory);
        
        verify(userRepositoryPort).existsById(userId);
        verify(getCouponByIdUseCase).execute(couponId);
        verify(cachePort).issueCouponAtomically(couponCounterKey, couponUserKey, 100L);
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
        
        // 쿠폰 정보 mock
        var coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .withQuantity(100, 0)
                .build();
        
        String lockKey = "coupon:lock:coupon_1";
        String couponCounterKey = "coupon:counter:1";
        String couponUserKey = "coupon:user:1:1";
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(getCouponByIdUseCase.execute(couponId)).thenReturn(coupon);
        when(keyGenerator.generateCouponCounterKey(couponId)).thenReturn(couponCounterKey);
        when(keyGenerator.generateCouponUserKey(couponId, userId)).thenReturn(couponUserKey);
        when(cachePort.issueCouponAtomically(couponCounterKey, couponUserKey, 100)).thenReturn(1L);
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(lockingPort.acquireLock(lockKey)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(userRepositoryPort).existsById(userId);
        verify(getCouponByIdUseCase).execute(couponId);
        verify(cachePort).issueCouponAtomically(couponCounterKey, couponUserKey, 100L);
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
        
        // 쿠폰 정보 mock
        var coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .withQuantity(100, 0)
                .build();
        
        String lockKey = "coupon:lock:coupon_1";
        String couponCounterKey = "coupon:counter:1";
        String couponUserKey = "coupon:user:1:1";
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(getCouponByIdUseCase.execute(couponId)).thenReturn(coupon);
        when(keyGenerator.generateCouponCounterKey(couponId)).thenReturn(couponCounterKey);
        when(keyGenerator.generateCouponUserKey(couponId, userId)).thenReturn(couponUserKey);
        when(cachePort.issueCouponAtomically(couponCounterKey, couponUserKey, 100)).thenReturn(1L);
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
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
            
        verify(userRepositoryPort).existsById(userId);
        verify(getCouponByIdUseCase).execute(couponId);
        verify(cachePort).issueCouponAtomically(couponCounterKey, couponUserKey, 100L);
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
    @DisplayName("동일 쿠폰에 대한 동시 발급 요청 시 Redis 원자적 연산으로 선착순 처리가 보장된다")
    void issueCoupon_ConcurrentSameCouponRequests_RedisAtomicProcessing() throws InterruptedException {
        // given
        Long couponId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;
        
        // 쿠폰 정보 mock
        var coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .withQuantity(100, 0)
                .build();
        
        CouponHistory expectedHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .couponId(couponId)
                .userId(userId1)
                .build();
        
        String lockKey = "coupon:lock:coupon_1";
        String couponCounterKey = "coupon:counter:1";
        String couponUserKey1 = "coupon:user:1:1";
        String couponUserKey2 = "coupon:user:1:2";
        
        // 공통 mock 설정
        when(userRepositoryPort.existsById(userId1)).thenReturn(true);
        when(userRepositoryPort.existsById(userId2)).thenReturn(true);
        when(getCouponByIdUseCase.execute(couponId)).thenReturn(coupon);
        when(keyGenerator.generateCouponCounterKey(couponId)).thenReturn(couponCounterKey);
        when(keyGenerator.generateCouponUserKey(couponId, userId1)).thenReturn(couponUserKey1);
        when(keyGenerator.generateCouponUserKey(couponId, userId2)).thenReturn(couponUserKey2);
        when(keyGenerator.generateCouponKey(couponId)).thenReturn(lockKey);
        when(lockingPort.acquireLock(lockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(anyLong(), eq(couponId))).thenReturn(expectedHistory);
        when(keyGenerator.generateCouponListCachePattern(anyLong())).thenReturn("coupon:list:user_*");
        
        // Redis 원자적 연산: 첫 번째는 성공, 두 번째는 실패 (한도 초과)
        when(cachePort.issueCouponAtomically(couponCounterKey, couponUserKey1, 100))
            .thenReturn(1L); // 첫 번째 발급 성공
        when(cachePort.issueCouponAtomically(couponCounterKey, couponUserKey2, 100))
            .thenReturn(-1L); // 두 번째 발급 실패 (한도 초과)
        when(cachePort.hasCouponIssued(couponUserKey2)).thenReturn(false); // 중복 발급 아님
        
        // when & then
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
            List.of(
                () -> {
                    try {
                        couponService.issueCoupon(couponId, userId1);
                    } catch (Exception e) {
                        throw new RuntimeException("USER1_FAILED: " + e.getMessage());
                    }
                },
                () -> {
                    try {
                        couponService.issueCoupon(couponId, userId2);
                    } catch (CouponException.OutOfStock e) {
                        throw new RuntimeException("OUT_OF_STOCK"); // 예상된 예외
                    } catch (Exception e) {
                        throw new RuntimeException("USER2_FAILED: " + e.getMessage());
                    }
                }
            )
        );
        
        assertThat(result.getSuccessCount()).isEqualTo(1); // 하나만 성공
        assertThat(result.getFailureCount()).isEqualTo(1); // 하나는 재고 부족으로 실패
        
        verify(getCouponByIdUseCase, times(2)).execute(couponId);
        verify(cachePort).issueCouponAtomically(couponCounterKey, couponUserKey1, 100);
        verify(cachePort).issueCouponAtomically(couponCounterKey, couponUserKey2, 100);
        verify(lockingPort, times(1)).acquireLock(lockKey); // 성공한 요청만 락 획득
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
        
        // 쿠폰 정보 mock
        var coupon1 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId1)
                .withQuantity(100, 0)
                .build();
        var coupon2 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId2)
                .withQuantity(100, 0)
                .build();
        
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
        String couponCounterKey1 = "coupon:counter:1";
        String couponCounterKey2 = "coupon:counter:2";
        String couponUserKey1 = "coupon:user:1:1";
        String couponUserKey2 = "coupon:user:2:2";
        
        // Mock 설정
        when(userRepositoryPort.existsById(userId1)).thenReturn(true);
        when(userRepositoryPort.existsById(userId2)).thenReturn(true);
        when(getCouponByIdUseCase.execute(couponId1)).thenReturn(coupon1);
        when(getCouponByIdUseCase.execute(couponId2)).thenReturn(coupon2);
        when(keyGenerator.generateCouponCounterKey(couponId1)).thenReturn(couponCounterKey1);
        when(keyGenerator.generateCouponCounterKey(couponId2)).thenReturn(couponCounterKey2);
        when(keyGenerator.generateCouponUserKey(couponId1, userId1)).thenReturn(couponUserKey1);
        when(keyGenerator.generateCouponUserKey(couponId2, userId2)).thenReturn(couponUserKey2);
        when(cachePort.issueCouponAtomically(couponCounterKey1, couponUserKey1, 100)).thenReturn(1L);
        when(cachePort.issueCouponAtomically(couponCounterKey2, couponUserKey2, 100)).thenReturn(1L);
        when(keyGenerator.generateCouponKey(couponId1)).thenReturn(lockKey1);
        when(keyGenerator.generateCouponKey(couponId2)).thenReturn(lockKey2);
        when(lockingPort.acquireLock(lockKey1)).thenReturn(true);
        when(lockingPort.acquireLock(lockKey2)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(issueCouponUseCase.execute(userId1, couponId1)).thenReturn(expectedHistory1);
        when(issueCouponUseCase.execute(userId2, couponId2)).thenReturn(expectedHistory2);
        when(keyGenerator.generateCouponListCachePattern(userId1)).thenReturn("coupon:list:user_1_*");
        when(keyGenerator.generateCouponListCachePattern(userId2)).thenReturn("coupon:list:user_2_*");
        
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
        
        verify(getCouponByIdUseCase).execute(couponId1);
        verify(getCouponByIdUseCase).execute(couponId2);
        verify(cachePort).issueCouponAtomically(couponCounterKey1, couponUserKey1, 100);
        verify(cachePort).issueCouponAtomically(couponCounterKey2, couponUserKey2, 100);
        verify(lockingPort).acquireLock(lockKey1);
        verify(lockingPort).acquireLock(lockKey2);
        verify(transactionTemplate, times(2)).execute(any());
        verify(issueCouponUseCase).execute(userId1, couponId1);
        verify(issueCouponUseCase).execute(userId2, couponId2);
        verify(lockingPort).releaseLock(lockKey1);
        verify(lockingPort).releaseLock(lockKey2);
    }
}