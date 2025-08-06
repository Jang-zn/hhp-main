package kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static kr.hhplus.be.server.util.TestAssertions.CouponAssertions;
import static kr.hhplus.be.server.util.TestAssertions.CommonAssertions;

/**
 * InMemoryCouponRepository 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 쿠폰 운영 시나리오를 반영한 테스트로 구성
 */
@DisplayName("쿠폰 저장소 비즈니스 시나리오")
class InMemoryCouponRepositoryTest {

    private InMemoryCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
    }

    @Test
    @DisplayName("유효한 쿠폰을 저장할 수 있다")
    void canSaveValidCoupon() {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon().build();
        
        // When
        Coupon saved = couponRepository.save(coupon);
        
        // Then
        CouponAssertions.assertSavedCorrectly(saved, coupon);
    }
    
    @ParameterizedTest
    @MethodSource("provideDiverseCouponData")
    @DisplayName("다양한 할인율과 발급량의 쿠폰을 저장할 수 있다")
    void canSaveDiverseCoupons(String code, String discountRate, int maxIssuance) {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
            .id(2L)
            .code(code)
            .discountRate(new BigDecimal(discountRate))
            .withQuantity(maxIssuance, 0)
            .build();
        
        // When
        Coupon saved = couponRepository.save(coupon);
        
        // Then
        CouponAssertions.assertSavedCorrectly(saved, coupon);
    }

    @Test
    @DisplayName("이미 만료된 쿠폰도 저장할 수 있다")
    void canSaveExpiredCoupon() {
        // Given
        Coupon expiredCoupon = TestBuilder.CouponBuilder.expiredCoupon().build();
        
        // When
        Coupon saved = couponRepository.save(expiredCoupon);
        
        // Then
        CouponAssertions.assertSavedCorrectly(saved, expiredCoupon);
        CouponAssertions.assertCouponExpired(saved);
    }

    @Test
    @DisplayName("발급량이 최대치를 초과한 쿠폰 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingOverIssuedCoupon() {
        // Given
        Coupon overIssuedCoupon = TestBuilder.CouponBuilder.overIssuedCoupon().build();
        
        // When & Then
        assertThatThrownBy(() -> couponRepository.save(overIssuedCoupon))
            .isInstanceOf(CouponException.InvalidCouponData.class);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦은 쿠폰 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingInvalidDateRangeCoupon() {
        // Given
        Coupon invalidCoupon = TestBuilder.CouponBuilder.invalidDatesCoupon().build();
        
        // When & Then
        assertThatThrownBy(() -> couponRepository.save(invalidCoupon))
            .isInstanceOf(CouponException.InvalidCouponData.class);
    }

    @Test
    @DisplayName("null 쿠폰 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingNullCoupon() {
        // When & Then
        assertThatThrownBy(() -> couponRepository.save(null))
            .isInstanceOf(CouponException.InvalidCouponData.class);
    }

    @Test
    @DisplayName("저장된 쿠폰을 ID로 조회할 수 있다")
    void canFindCouponById() {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon().build();
        Coupon saved = couponRepository.save(coupon);
        
        // When
        Optional<Coupon> found = couponRepository.findById(saved.getId());
        
        // Then
        assertThat(found).isPresent();
        CouponAssertions.assertSavedCorrectly(found.get(), coupon);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindingNonExistentCoupon() {
        // When
        Optional<Coupon> found = couponRepository.findById(999L);
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null ID로 조회 시 예외가 발생한다")
    void throwsExceptionWhenFindingWithNullId() {
        // When & Then
        assertThatThrownBy(() -> couponRepository.findById(null))
            .isInstanceOf(CouponException.CouponIdCannotBeNull.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidCouponIds")
    @DisplayName("유효하지 않은 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindingWithInvalidIds(Long invalidId) {
        // When
        Optional<Coupon> found = couponRepository.findById(invalidId);
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 쿠폰들을 동시에 저장할 수 있다")
    void canSaveDifferentCouponsConcurrently() {
        // Given
        int numberOfCoupons = 20;
        
        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfCoupons, () -> {
                Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                    .id(System.nanoTime()) // 고유 ID 생성
                    .code("CONCURRENT_" + System.nanoTime())
                    .build();
                return couponRepository.save(coupon);
            });
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfCoupons);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일한 쿠폰을 동시에 업데이트할 수 있다")
    void canUpdateSameCouponConcurrently() {
        // Given
        Long couponId = 500L;
        Coupon initialCoupon = TestBuilder.CouponBuilder.defaultCoupon()
            .id(couponId)
            .code("CONCURRENT_UPDATE")
            .build();
        couponRepository.save(initialCoupon);
        
        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                Coupon updated = TestBuilder.CouponBuilder.defaultCoupon()
                    .id(couponId)
                    .code("CONCURRENT_UPDATE")
                    .discountRate(new BigDecimal("0.15"))
                    .withQuantity(1000, (int)(Math.random() * 100))
                    .build();
                return couponRepository.save(updated);
            });
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        Optional<Coupon> finalCoupon = couponRepository.findById(couponId);
        assertThat(finalCoupon).isPresent();
        assertThat(finalCoupon.get().getDiscountRate()).isEqualByComparingTo(new BigDecimal("0.15"));
    }

    @Test
    @DisplayName("쿠폰 조회와 저장이 동시에 실행될 수 있다")
    void canReadAndWriteConcurrently() {
        // Given
        Long couponId = 600L;
        Coupon baseCoupon = TestBuilder.CouponBuilder.defaultCoupon()
            .id(couponId)
            .code("READ_WRITE_TEST")
            .build();
        couponRepository.save(baseCoupon);

        // When - 읽기와 쓰기 작업을 동시에 실행
        List<Runnable> tasks = List.of(
            // 읽기 작업들
            () -> {
                for (int i = 0; i < 10; i++) {
                    couponRepository.findById(couponId);
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            },
            () -> {
                for (int i = 0; i < 10; i++) {
                    couponRepository.findById(couponId);
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            },
            // 쓰기 작업들
            () -> {
                for (int i = 0; i < 5; i++) {
                    Coupon updated = TestBuilder.CouponBuilder.defaultCoupon()
                        .id(couponId)
                        .code("READ_WRITE_TEST")
                        .withQuantity(500, i + 1)
                        .build();
                    couponRepository.save(updated);
                    try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            },
            () -> {
                for (int i = 0; i < 5; i++) {
                    Coupon updated = TestBuilder.CouponBuilder.defaultCoupon()
                        .id(couponId)
                        .code("READ_WRITE_TEST")
                        .withQuantity(500, i + 10)
                        .build();
                    couponRepository.save(updated);
                    try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        );
        
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeMultipleTasks(tasks);
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(4);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        Optional<Coupon> finalCoupon = couponRepository.findById(couponId);
        assertThat(finalCoupon).isPresent();
    }

    @Test
    @DisplayName("만료된 쿠폰들을 특정 상태를 제외하고 조회할 수 있다")
    void canFindExpiredCouponsExcludingSpecificStatuses() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // 만료된 ACTIVE 쿠폰
        Coupon expiredActive = TestBuilder.CouponBuilder.expiredCoupon()
            .id(1L).code("EXPIRED_ACTIVE").build();
        
        // 만료된 SOLD_OUT 쿠폰
        Coupon expiredSoldOut = TestBuilder.CouponBuilder.expiredCoupon()
            .id(2L).code("EXPIRED_SOLDOUT")
            .status(CouponStatus.SOLD_OUT).build();
        
        // 이미 EXPIRED 상태인 쿠폰 (제외되어야 함)
        Coupon alreadyExpired = TestBuilder.CouponBuilder.expiredCoupon()
            .id(3L).code("ALREADY_EXPIRED")
            .status(CouponStatus.EXPIRED).build();
        
        // 유효한 쿠폰 (제외되어야 함)
        Coupon validCoupon = TestBuilder.CouponBuilder.defaultCoupon()
            .id(4L).code("VALID").build();
        
        couponRepository.save(expiredActive);
        couponRepository.save(expiredSoldOut);
        couponRepository.save(alreadyExpired);
        couponRepository.save(validCoupon);
        
        // When
        List<Coupon> expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
            now, CouponStatus.EXPIRED, CouponStatus.DISABLED
        );
        
        // Then
        assertThat(expiredCoupons).hasSize(2);
        assertThat(expiredCoupons).extracting(Coupon::getCode)
            .containsExactlyInAnyOrder("EXPIRED_ACTIVE", "EXPIRED_SOLDOUT");
    }

    @Test
    @DisplayName("만료된 쿠폰이 없으면 빈 결과를 반환한다")
    void returnsEmptyWhenNoExpiredCoupons() {
        // Given
        Coupon validCoupon = TestBuilder.CouponBuilder.defaultCoupon().build();
        couponRepository.save(validCoupon);
        
        // When
        List<Coupon> expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
            LocalDateTime.now(), CouponStatus.EXPIRED, CouponStatus.DISABLED
        );
        
        // Then
        assertThat(expiredCoupons).isEmpty();
    }

    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideDiverseCouponData() {
        return Stream.of(
            Arguments.of("WELCOME10", "0.10", 1000),
            Arguments.of("SUMMER25", "0.25", 500),
            Arguments.of("VIP30", "0.30", 100)
        );
    }

    private static Stream<Arguments> provideInvalidCouponIds() {
        return Stream.of(
            Arguments.of(0L),
            Arguments.of(-1L),
            Arguments.of(-999L)
        );
    }
}