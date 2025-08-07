package kr.hhplus.be.server.unit.concurrency;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 발급 동시성 단위 테스트")
class CouponIssueConcurrencyTest {

    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;

    private IssueCouponUseCase issueCouponUseCase;
    private Coupon testCoupon;
    private User testUser;

    @BeforeEach
    void setUp() {
        issueCouponUseCase = new IssueCouponUseCase(
                userRepositoryPort,
                couponRepositoryPort, 
                couponHistoryRepositoryPort
        );
        
        testCoupon = Coupon.builder()
                .id(1L)
                .code("CONCURRENT_TEST")
                .discountRate(new BigDecimal("10.0"))
                .maxIssuance(100)
                .issuedCount(0)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();

        testUser = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
    }

    @Test
    @DisplayName("동시 쿠폰 발급 시 발급 수량 초과 방지 확인")
    void testConcurrentCouponIssue_PreventOverIssue() {
        // Given: 최대 발급 수량 10개인 쿠폰
        Coupon limitedCoupon = Coupon.builder()
                .id(1L)
                .code("LIMITED_COUPON")
                .discountRate(new BigDecimal("20.0"))
                .maxIssuance(10)
                .issuedCount(0)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();

        AtomicReference<Coupon> couponRef = new AtomicReference<>(limitedCoupon);
        when(couponRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(couponRef.get()));
        when(userRepositoryPort.findById(anyLong()))
                .thenReturn(Optional.of(testUser));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(anyLong(), anyLong()))
                .thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class)))
                .thenAnswer(invocation -> {
                    Coupon saved = invocation.getArgument(0);
                    couponRef.set(saved);
                    return saved;
                });

        // When: 20개 스레드가 동시에 쿠폰 발급 요청
        int threadCount = 20;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> {
                    // 각 스레드마다 다른 사용자 ID 사용
                    long userId = Thread.currentThread().getId();
                    return issueCouponUseCase.execute(userId, 1L);
                }
        );

        // Then: 최대 10개만 성공해야 함
        Coupon finalCoupon = couponRef.get();
        System.out.printf("동시 쿠폰 발급 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 발급 수량: %d (최대: 10)%n", finalCoupon.getIssuedCount());

        // 발급 수량이 최대값을 초과하지 않아야 함
        assertThat(finalCoupon.getIssuedCount()).isLessThanOrEqualTo(10);
        
        // 일부 요청은 실패해야 함
        assertThat(result.getFailureCount()).isGreaterThan(0);
        
        // 성공한 요청 수와 실제 발급 수량이 일치해야 함
        assertThat(result.getSuccessCount()).isEqualTo(finalCoupon.getIssuedCount());
    }

    @Test
    @DisplayName("동시 쿠폰 발급 시 race condition 확인")
    void testConcurrentCouponIssue_RaceCondition() {
        // Given: 발급 가능 수량이 충분한 쿠폰
        AtomicReference<Coupon> couponRef = new AtomicReference<>(testCoupon);
        when(couponRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(couponRef.get()));
        when(userRepositoryPort.findById(anyLong()))
                .thenReturn(Optional.of(testUser));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(anyLong(), anyLong()))
                .thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class)))
                .thenAnswer(invocation -> {
                    Coupon saved = invocation.getArgument(0);
                    couponRef.set(saved);
                    return saved;
                });

        // When: 10개 스레드가 동시에 쿠폰 발급 요청
        int threadCount = 10;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> {
                    // 각 스레드마다 다른 사용자 ID 사용
                    long userId = Thread.currentThread().getId();
                    return issueCouponUseCase.execute(userId, 1L);
                }
        );

        // Then: 결과 분석
        Coupon finalCoupon = couponRef.get();
        System.out.printf("Race condition 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 발급 수량: %d%n", finalCoupon.getIssuedCount());

        // 모든 요청이 성공했다면 정상 작동
        if (result.getSuccessCount() == threadCount) {
            assertThat(finalCoupon.getIssuedCount()).isEqualTo(threadCount);
        } else {
            // Race condition이 발생한 경우
            System.out.println("Race condition으로 인한 일부 실패 발생");
            assertThat(finalCoupon.getIssuedCount()).isLessThanOrEqualTo(threadCount);
        }
    }

    @Test
    @DisplayName("만료된 쿠폰 동시 발급 시도")
    void testConcurrentExpiredCouponIssue() {
        // Given: 만료된 쿠폰
        Coupon expiredCoupon = Coupon.builder()
                .id(1L)
                .code("EXPIRED_COUPON")
                .discountRate(new BigDecimal("15.0"))
                .maxIssuance(100)
                .issuedCount(0)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1)) // 이미 만료
                .build();

        when(couponRepositoryPort.findByIdWithLock(1L))
                .thenReturn(Optional.of(expiredCoupon));
        when(userRepositoryPort.findById(anyLong()))
                .thenReturn(Optional.of(testUser));

        // When: 5개 스레드가 동시에 만료된 쿠폰 발급 시도
        int threadCount = 5;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> {
                    long userId = Thread.currentThread().getId();
                    return issueCouponUseCase.execute(userId, 1L);
                }
        );

        // Then: 모든 요청이 실패해야 함
        System.out.printf("만료 쿠폰 발급 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(threadCount);
        
        // 에러 메시지 확인
        result.getErrorMessages().forEach(System.out::println);
    }

    @Test
    @DisplayName("동일 사용자 중복 발급 방지 테스트")
    void testConcurrentDuplicateIssuePreventionForSameUser() {
        // Given: 동일 사용자가 이미 발급받은 쿠폰
        AtomicReference<Coupon> couponRef = new AtomicReference<>(testCoupon);
        when(couponRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(couponRef.get()));
        when(userRepositoryPort.findById(1L))
                .thenReturn(Optional.of(testUser));
        
        // 첫 번째 호출에서는 중복이 아니지만, 이후 호출에서는 중복
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(1L, 1L))
                .thenReturn(false) // 첫 번째 호출
                .thenReturn(true); // 이후 호출들
        
        when(couponRepositoryPort.save(any(Coupon.class)))
                .thenAnswer(invocation -> {
                    Coupon saved = invocation.getArgument(0);
                    couponRef.set(saved);
                    return saved;
                });

        // When: 동일 사용자가 5개 스레드로 동시 발급 요청
        int threadCount = 5;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> issueCouponUseCase.execute(1L, 1L) // 모두 동일한 사용자 ID 사용
        );

        // Then: 최대 1개만 성공해야 함
        System.out.printf("중복 발급 방지 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());

        // 동일 사용자에게는 1개만 발급되어야 함
        assertThat(result.getSuccessCount()).isLessThanOrEqualTo(1);
        assertThat(result.getFailureCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("대량 동시 쿠폰 발급 부하 테스트")
    void testHighConcurrencyCouponIssue() {
        // Given: 충분한 발급 한도를 가진 쿠폰
        Coupon largeCoupon = Coupon.builder()
                .id(1L)
                .code("LOAD_TEST_COUPON")
                .discountRate(new BigDecimal("5.0"))
                .maxIssuance(500)
                .issuedCount(0)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();

        AtomicReference<Coupon> couponRef = new AtomicReference<>(largeCoupon);
        when(couponRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(couponRef.get()));
        when(userRepositoryPort.findById(anyLong()))
                .thenReturn(Optional.of(testUser));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(anyLong(), anyLong()))
                .thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class)))
                .thenAnswer(invocation -> {
                    Coupon saved = invocation.getArgument(0);
                    couponRef.set(saved);
                    return saved;
                });

        // When: 200회 실행, 25개 동시 스레드
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeLoadTest(
                () -> {
                    // 각 실행마다 다른 사용자 ID 사용
                    long userId = System.nanoTime() % 10000000; // 충분히 고유한 ID
                    issueCouponUseCase.execute(userId, 1L);
                    return null;
                },
                200, // 총 실행 횟수
                25   // 동시 스레드 수
        );

        // Then: 성능 및 정확성 검증
        Coupon finalCoupon = couponRef.get();
        System.out.printf("쿠폰 부하 테스트 결과: 성공 %d, 실패 %d, 실행시간 %dms%n", 
                result.getSuccessCount(), result.getFailureCount(), result.getExecutionTimeMs());
        System.out.printf("최종 발급 수량: %d%n", finalCoupon.getIssuedCount());

        // 성공률이 높아야 함 (발급 한도가 충분하므로)
        assertThat(result.getSuccessRate()).isGreaterThan(90.0);
        assertThat(finalCoupon.getIssuedCount()).isEqualTo(result.getSuccessCount());
        assertThat(finalCoupon.getIssuedCount()).isLessThanOrEqualTo(500);
        
        // 적절한 성능 기준
        assertThat(result.getExecutionTimeMs()).isLessThan(2000);
    }
}