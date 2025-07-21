package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import kr.hhplus.be.server.domain.exception.CouponException;

@DisplayName("IssueCouponUseCase 단위 테스트")
class IssueCouponUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;

    private IssueCouponUseCase issueCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        issueCouponUseCase = new IssueCouponUseCase(
                userRepositoryPort, couponRepositoryPort, couponHistoryRepositoryPort, lockingPort
        );
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getCoupon()).isEqualTo(coupon);
        assertThat(result.getIssuedAt()).isNotNull();
        
        verify(lockingPort).acquireLock("coupon-issue-" + couponId);
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
        verify(couponRepositoryPort).save(coupon);
        verify(couponHistoryRepositoryPort).save(any(CouponHistory.class));
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰 발급 시나리오")
    void issueCoupon_WithDifferentScenarios(Long userId, Long couponId, String couponCode) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code(couponCode)
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(200)
                .issuedCount(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(15))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCoupon().getCode()).isEqualTo(couponCode);
        
        verify(lockingPort).acquireLock("coupon-issue-" + couponId);
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 발급 시 예외 발생")
    void issueCoupon_UserNotFound() {
        // given
        Long userId = 999L;
        Long couponId = 1L;
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(CouponException.Messages.USER_NOT_FOUND);
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외 발생")
    void issueCoupon_CouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 999L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(CouponException.Messages.COUPON_NOT_FOUND);
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

        @Test
        @DisplayName("실패케이스: 만료된 쿠폰 발급")
        void issueCoupon_ExpiredCoupon() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon expiredCoupon = Coupon.builder()
                .code("EXPIRED")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1)) // 이미 만료
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(expiredCoupon));

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.Expired.class)
                .hasMessage(CouponException.Messages.COUPON_EXPIRED);
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("재고 소진된 쿠폰 발급 시 예외 발생")
    void issueCoupon_OutOfStock() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon outOfStockCoupon = Coupon.builder()
                .code("OUTOFSTOCK")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(100)
                .issuedCount(100) // 재고 소진
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(outOfStockCoupon));

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.OutOfStock.class)
                .hasMessage(CouponException.Messages.COUPON_OUT_OF_STOCK);
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 재발급 시 예외 발생")
    void issueCoupon_AlreadyIssued() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("ALREADY_ISSUED")
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(true);
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.AlreadyIssued.class)
                .hasMessage(CouponException.Messages.COUPON_ALREADY_ISSUED);
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("null 사용자 ID로 쿠폰 발급 시 예외 발생")
    void issueCoupon_WithNullUserId() {
        // given
        Long userId = null;
        Long couponId = 1L;

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 쿠폰 ID로 쿠폰 발급 시 예외 발생")
    void issueCoupon_WithNullCouponId() {
        // given
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("아직 시작되지 않은 쿠폰 발급 시 예외 발생")
    void issueCoupon_NotYetStarted() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon futureStartCoupon = Coupon.builder()
                .code("FUTURE_START")
                .discountRate(new BigDecimal("0.25"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().plusDays(1)) // 아직 시작 안함
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(futureStartCoupon));
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet started");
                
        verify(lockingPort).releaseLock("coupon-issue-" + couponId);
    }

    @Test
    @DisplayName("락 획득 실패 시 예외 발생")
    void issueCoupon_LockAcquisitionFailed() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        when(lockingPort.acquireLock(anyString())).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.ConcurrencyConflict.class)
                .hasMessage(CouponException.Messages.COUPON_CONCURRENCY_CONFLICT);
                
        verify(lockingPort, never()).releaseLock(anyString());
    }

    @ParameterizedTest
    @MethodSource("provideInvalidIds")
    @DisplayName("비정상 ID 값들로 쿠폰 발급 테스트")
    void issueCoupon_WithInvalidIds(Long userId, Long couponId) {
        // when & then
        if (userId != null && userId > 0) {
            User user = User.builder().name("테스트").build();
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        }
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of(1L, 1L, "WELCOME10"),
                Arguments.of(2L, 2L, "SUMMER25"),
                Arguments.of(3L, 3L, "VIP30")
        );
    }

    private static Stream<Arguments> provideInvalidIds() {
        return Stream.of(
                Arguments.of(999L, 999L), // 존재하지 않는 ID들
                Arguments.of(888L, 888L)
        );
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동시 쿠폰 발급 요청 시 한 명만 성공")
        void issueCoupon_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
            // given
            Long userId1 = 1L;
            Long userId2 = 2L;
            Long couponId = 1L;
            
            User user1 = User.builder().name("사용자1").build();
            User user2 = User.builder().name("사용자2").build();
            
            Coupon coupon = Coupon.builder()
                    .code("LIMITED1")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(1) // 재고 1개
                    .issuedCount(0)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .build();
            
            AtomicInteger lockCounter = new AtomicInteger(0);
            
            when(userRepositoryPort.findById(userId1)).thenReturn(Optional.of(user1));
            when(userRepositoryPort.findById(userId2)).thenReturn(Optional.of(user2));
            when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
            when(couponHistoryRepositoryPort.existsByUserAndCoupon(any(), any())).thenReturn(false);
            when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
            when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
            
            // 락 획득 시뮬레이션: 첫 번째만 성공
            when(lockingPort.acquireLock(anyString())).thenAnswer(invocation -> {
                return lockCounter.getAndIncrement() == 0;
            });
            
            ExecutorService executor = Executors.newFixedThreadPool(2);
            
            // when
            CompletableFuture<CouponHistory> future1 = CompletableFuture.supplyAsync(() -> {
                return issueCouponUseCase.execute(userId1, couponId);
            }, executor).handle((result, ex) -> {
                if (ex != null) {
                    return null; // 예외 발생 시 null 반환
                }
                return result;
            });
            
            CompletableFuture<CouponHistory> future2 = CompletableFuture.supplyAsync(() -> {
                return issueCouponUseCase.execute(userId2, couponId);
            }, executor).handle((result, ex) -> {
                if (ex != null) {
                    return null; // 예외 발생 시 null 반환
                }
                return result;
            });
            
            // then
            CompletableFuture.allOf(future1, future2).join();
            
            CouponHistory result1 = future1.join();
            CouponHistory result2 = future2.join();
            
            // 한 명만 성공해야 함
            int successCount = (result1 != null ? 1 : 0) + (result2 != null ? 1 : 0);
            
            assertThat(successCount).isEqualTo(1);
            
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
        
        @Test
        @DisplayName("동시성 하에서 락 획득 실패 테스트")
        void issueCoupon_LockContentionHandling() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock(anyString())).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                    .isInstanceOf(CouponException.ConcurrencyConflict.class)
                    .hasMessage(CouponException.Messages.COUPON_CONCURRENCY_CONFLICT);
            
            verify(lockingPort).acquireLock("coupon-issue-" + couponId);
            verify(lockingPort, never()).releaseLock(anyString());
        }
    }
}