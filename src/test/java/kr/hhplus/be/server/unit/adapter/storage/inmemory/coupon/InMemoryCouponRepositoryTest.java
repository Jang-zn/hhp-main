package kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryCouponRepository 단위 테스트")
class InMemoryCouponRepositoryTest {

    private InMemoryCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
    }

    @Nested
    @DisplayName("쿠폰 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 쿠폰 저장")
        void save_Success() {
        // given
        Product product = Product.builder()
                .id(1L)
                .name("노트북")
                .price(new BigDecimal("1200000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .productId(product.getId())
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);

        // then
        assertThat(savedCoupon).isNotNull();
        assertThat(savedCoupon.getCode()).isEqualTo("DISCOUNT10");
        assertThat(savedCoupon.getDiscountRate()).isEqualTo(new BigDecimal("0.10"));
        assertThat(savedCoupon.getMaxIssuance()).isEqualTo(100);
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon.InMemoryCouponRepositoryTest#provideCouponData")
        @DisplayName("성공케이스: 다양한 쿠폰 데이터로 저장")
        void save_WithDifferentCouponData(String code, String discountRate, int maxIssuance) {
            // given
            Coupon coupon = Coupon.builder()
                    .id(2L)
                    .code(code)
                    .discountRate(new BigDecimal(discountRate))
                    .maxIssuance(maxIssuance)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            // when
            Coupon savedCoupon = couponRepository.save(coupon);

            // then
            assertThat(savedCoupon).isNotNull();
            assertThat(savedCoupon.getCode()).isEqualTo(code);
            assertThat(savedCoupon.getDiscountRate()).isEqualTo(new BigDecimal(discountRate));
            assertThat(savedCoupon.getMaxIssuance()).isEqualTo(maxIssuance);
        }

        @Test
        @DisplayName("성공케이스: 이미 만료된 쿠폰 저장")
        void save_ExpiredCoupon() {
            // given
            Coupon expiredCoupon = Coupon.builder()
                    .id(3L)
                    .code("EXPIRED")
                    .discountRate(new BigDecimal("0.20"))
                    .maxIssuance(100)
                    .issuedCount(50)
                    .startDate(LocalDateTime.now().minusDays(10))
                    .endDate(LocalDateTime.now().minusDays(1))
                    .status(CouponStatus.EXPIRED)
                    .build();

            // when
            Coupon savedCoupon = couponRepository.save(expiredCoupon);

            // then
            assertThat(savedCoupon).isNotNull();
            assertThat(savedCoupon.getEndDate()).isBefore(LocalDateTime.now());
        }

        @Test
        @DisplayName("실패케이스: 발급 수량이 최대 발급 수를 초과한 쿠폰")
        void save_CouponWithExceededIssuance() {
            // given
            Coupon overIssuedCoupon = Coupon.builder()
                    .id(4L)
                    .code("OVERISSUED")
                    .discountRate(new BigDecimal("0.15"))
                    .maxIssuance(100)
                    .issuedCount(150) // 최대 발급 수 초과
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.SOLD_OUT)
                    .build();

            // when & then
            assertThatThrownBy(() -> couponRepository.save(overIssuedCoupon))
                    .isInstanceOf(CouponException.InvalidCouponData.class);
        }

        @Test
        @DisplayName("성공케이스: 할인율이 100%를 초과하는 쿠폰")
        void save_CouponWithExcessiveDiscountRate() {
            // given
            Coupon excessiveCoupon = Coupon.builder()
                    .id(5L)
                    .code("EXCESSIVE")
                    .discountRate(new BigDecimal("1.50")) // 150% 할인
                    .maxIssuance(10)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(7))
                    .status(CouponStatus.ACTIVE)
                    .build();

            // when
            Coupon savedCoupon = couponRepository.save(excessiveCoupon);

            // then
            assertThat(savedCoupon).isNotNull();
            assertThat(savedCoupon.getDiscountRate()).isGreaterThan(new BigDecimal("1.0"));
        }

        @Test
        @DisplayName("실패케이스: 시작일이 종료일보다 늦은 쿠폰")
        void save_CouponWithInvalidDateRange() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Coupon invalidDateCoupon = Coupon.builder()
                    .id(6L)
                    .code("INVALIDDATE")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(50)
                    .issuedCount(0)
                    .startDate(now.plusDays(10))
                    .endDate(now.plusDays(5)) // 시작일보다 빠른 종료일
                    .status(CouponStatus.INACTIVE)
                    .build();

            // when & then
            assertThatThrownBy(() -> couponRepository.save(invalidDateCoupon))
                    .isInstanceOf(CouponException.InvalidCouponData.class);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon.InMemoryCouponRepositoryTest#provideEdgeCaseDiscountRates")
        @DisplayName("성공케이스: 극한값 할인율으로 쿠폰 저장")
        void save_WithEdgeCaseDiscountRates(String description, String discountRate) {
            // given
            Coupon coupon = Coupon.builder()
                    .id(7L)
                    .code("EDGE_" + description)
                    .discountRate(new BigDecimal(discountRate))
                    .maxIssuance(100)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            // when
            Coupon savedCoupon = couponRepository.save(coupon);

            // then
            assertThat(savedCoupon).isNotNull();
            assertThat(savedCoupon.getDiscountRate()).isEqualTo(new BigDecimal(discountRate));
        }

        @Test
        @DisplayName("실패케이스: null 쿠폰 객체 저장")
        void save_WithNullCoupon() {
            // when & then
            assertThatThrownBy(() -> couponRepository.save(null))
                    .isInstanceOf(CouponException.InvalidCouponData.class);
        }
    }

    @Nested
    @DisplayName("쿠폰 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 쿠폰 ID로 조회")
        void findById_Success() {
        // given
        Coupon coupon = Coupon.builder()
                .id(10L)
                .code("SALE20")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(50)
                .issuedCount(10)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(15))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        Optional<Coupon> foundCoupon = couponRepository.findById(savedCoupon.getId());

        // then
        assertThat(foundCoupon).isPresent();
        assertThat(foundCoupon.get().getCode()).isEqualTo("SALE20");
            assertThat(foundCoupon.get().getDiscountRate()).isEqualTo(new BigDecimal("0.20"));
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 쿠폰 조회")
        void findById_NotFound() {
            // when
            Optional<Coupon> foundCoupon = couponRepository.findById(999L);

            // then
            assertThat(foundCoupon).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 쿠폰 ID로 조회")
        void findById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> couponRepository.findById(null))
                    .isInstanceOf(CouponException.CouponIdCannotBeNull.class);
        }

        @Test
        @DisplayName("실패케이스: 음수 쿠폰 ID로 조회")
        void findById_WithNegativeId() {
            // when
            Optional<Coupon> foundCoupon = couponRepository.findById(-1L);

            // then
            assertThat(foundCoupon).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon.InMemoryCouponRepositoryTest#provideInvalidCouponIds")
        @DisplayName("실패케이스: 유효하지 않은 쿠폰 ID들로 조회")
        void findById_WithInvalidIds(Long invalidId) {
            // when
            Optional<Coupon> foundCoupon = couponRepository.findById(invalidId);

            // then
            assertThat(foundCoupon).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 쿠폰 동시 저장")
        void save_ConcurrentSaveForDifferentCoupons() throws Exception {
            // given
            int numberOfCoupons = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfCoupons);
            AtomicInteger successCount = new AtomicInteger(0);

            // when - 서로 다른 쿠폰들을 동시에 저장
            for (int i = 0; i < numberOfCoupons; i++) {
                final int couponIndex = i + 1;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Coupon coupon = Coupon.builder()
                                .id((long) couponIndex)
                                .code("CONCURRENT" + couponIndex)
                                .discountRate(new BigDecimal("0.10"))
                                .maxIssuance(100)
                                .issuedCount(0)
                                .startDate(LocalDateTime.now())
                                .endDate(LocalDateTime.now().plusDays(30))
                                .build();
                        
                        couponRepository.save(coupon);
                        successCount.incrementAndGet();
                        Thread.sleep(1);
                    } catch (Exception e) {
                        System.err.println("Error for coupon " + couponIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // then - 모든 쿠폰이 성공적으로 저장되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfCoupons);
            
            // 각 쿠폰이 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfCoupons; i++) {
                Optional<Coupon> coupon = couponRepository.findById((long) i);
                assertThat(coupon).isPresent();
                assertThat(coupon.get().getCode()).isEqualTo("CONCURRENT" + i);
            }

            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 쿠폰 동시 업데이트")
        void save_ConcurrentUpdateForSameCoupon() throws Exception {
            // given
            Long couponId = 500L;
            Coupon initialCoupon = Coupon.builder()
                    .id(couponId)
                    .code("CONCURRENT_UPDATE")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(1000)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();
            couponRepository.save(initialCoupon);
            int numberOfThreads = 5;
            int updatesPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            // when - 동일한 쿠폰을 동시에 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            Coupon updatedCoupon = Coupon.builder()
                                    .id(couponId)
                                    .code("CONCURRENT_UPDATE")
                                    .discountRate(new BigDecimal("0.15"))
                                    .maxIssuance(1000)
                                    .issuedCount(threadId * updatesPerThread + j + 1)
                                    .startDate(LocalDateTime.now())
                                    .endDate(LocalDateTime.now().plusDays(30))
                                    .build();
                            
                            couponRepository.save(updatedCoupon);
                            successfulUpdates.incrementAndGet();
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        System.err.println("Update error for thread " + threadId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // then
            assertThat(successfulUpdates.get()).isEqualTo(numberOfThreads * updatesPerThread);
            
            // 최종 상태 확인
            Optional<Coupon> finalCoupon = couponRepository.findById(couponId);
            assertThat(finalCoupon).isPresent();
            assertThat(finalCoupon.get().getDiscountRate()).isEqualTo(new BigDecimal("0.15"));
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            Long couponId = 600L;
            Coupon baseCoupon = Coupon.builder()
                    .id(couponId)
                    .code("READ_WRITE_TEST")
                    .discountRate(new BigDecimal("0.20"))
                    .maxIssuance(500)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();
            couponRepository.save(baseCoupon);

            int numberOfReaders = 3;
            int numberOfWriters = 3;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfReaders + numberOfWriters);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);

            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 10; j++) {
                            Optional<Coupon> coupon = couponRepository.findById(couponId);
                            if (coupon.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 5; j++) {
                            Coupon updatedCoupon = Coupon.builder()
                                    .id(couponId)
                                    .code("READ_WRITE_TEST")
                                    .discountRate(new BigDecimal("0.20"))
                                    .maxIssuance(500)
                                    .issuedCount(writerId * 5 + j + 1)
                                    .startDate(LocalDateTime.now())
                                    .endDate(LocalDateTime.now().plusDays(30))
                                    .build();
                            
                            couponRepository.save(updatedCoupon);
                            successfulWrites.incrementAndGet();
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 5);
            
            // 최종 상태 확인
            Optional<Coupon> finalCoupon = couponRepository.findById(couponId);
            assertThat(finalCoupon).isPresent();

            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    @Nested
    @DisplayName("만료 쿠폰 조회 테스트")
    class ExpirationTests {
        
        @Test
        @DisplayName("성공케이스: 만료된 쿠폰 조회")
        void findExpiredCouponsNotInStatus_Success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            
            // 만료된 ACTIVE 쿠폰
            Coupon expiredActiveCoupon = Coupon.builder()
                    .id(1L)
                    .code("EXPIRED_ACTIVE")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(100)
                    .issuedCount(50)
                    .startDate(now.minusDays(10))
                    .endDate(now.minusDays(1))
                    .status(CouponStatus.ACTIVE)
                    .build();
            
            // 만료된 SOLD_OUT 쿠폰
            Coupon expiredSoldOutCoupon = Coupon.builder()
                    .id(2L)
                    .code("EXPIRED_SOLDOUT")
                    .discountRate(new BigDecimal("0.15"))
                    .maxIssuance(100)
                    .issuedCount(100)
                    .startDate(now.minusDays(20))
                    .endDate(now.minusDays(5))
                    .status(CouponStatus.SOLD_OUT)
                    .build();
            
            // 이미 EXPIRED 상태인 쿠폰 (제외되어야 함)
            Coupon alreadyExpiredCoupon = Coupon.builder()
                    .id(3L)
                    .code("ALREADY_EXPIRED")
                    .discountRate(new BigDecimal("0.20"))
                    .maxIssuance(100)
                    .issuedCount(50)
                    .startDate(now.minusDays(15))
                    .endDate(now.minusDays(2))
                    .status(CouponStatus.EXPIRED)
                    .build();
            
            // DISABLED 상태인 쿠폰 (제외되어야 함)
            Coupon disabledCoupon = Coupon.builder()
                    .id(4L)
                    .code("DISABLED")
                    .discountRate(new BigDecimal("0.25"))
                    .maxIssuance(100)
                    .issuedCount(10)
                    .startDate(now.minusDays(30))
                    .endDate(now.minusDays(3))
                    .status(CouponStatus.DISABLED)
                    .build();
            
            // 유효한 쿠폰 (제외되어야 함)
            Coupon validCoupon = Coupon.builder()
                    .id(5L)
                    .code("VALID")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(100)
                    .issuedCount(30)
                    .startDate(now.minusDays(5))
                    .endDate(now.plusDays(10))
                    .status(CouponStatus.ACTIVE)
                    .build();
            
            couponRepository.save(expiredActiveCoupon);
            couponRepository.save(expiredSoldOutCoupon);
            couponRepository.save(alreadyExpiredCoupon);
            couponRepository.save(disabledCoupon);
            couponRepository.save(validCoupon);
            
            // when
            List<Coupon> expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
                    now, CouponStatus.EXPIRED, CouponStatus.DISABLED
            );
            
            // then
            assertThat(expiredCoupons).hasSize(2);
            assertThat(expiredCoupons).extracting(Coupon::getCode)
                    .containsExactlyInAnyOrder("EXPIRED_ACTIVE", "EXPIRED_SOLDOUT");
        }
        
        @Test
        @DisplayName("성공케이스: 만료된 쿠폰이 없는 경우")
        void findExpiredCouponsNotInStatus_NoExpiredCoupons() {
            // given
            LocalDateTime now = LocalDateTime.now();
            
            Coupon validCoupon = Coupon.builder()
                    .id(1L)
                    .code("VALID_COUPON")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(100)
                    .issuedCount(30)
                    .startDate(now.minusDays(5))
                    .endDate(now.plusDays(10))
                    .status(CouponStatus.ACTIVE)
                    .build();
            
            couponRepository.save(validCoupon);
            
            // when
            List<Coupon> expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
                    now, CouponStatus.EXPIRED, CouponStatus.DISABLED
            );
            
            // then
            assertThat(expiredCoupons).isEmpty();
        }
        
        @Test
        @DisplayName("성공케이스: 다양한 상태 제외 조건")
        void findExpiredCouponsNotInStatus_WithDifferentExclusions() {
            // given
            LocalDateTime now = LocalDateTime.now();
            
            Coupon expiredActiveCoupon = Coupon.builder()
                    .id(1L)
                    .code("EXPIRED_ACTIVE")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(100)
                    .issuedCount(50)
                    .startDate(now.minusDays(10))
                    .endDate(now.minusDays(1))
                    .status(CouponStatus.ACTIVE)
                    .build();
            
            couponRepository.save(expiredActiveCoupon);
            
            // when - ACTIVE 상태만 제외
            List<Coupon> expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
                    now, CouponStatus.ACTIVE
            );
            
            // then - ACTIVE 상태는 제외되므로 빈 결과
            assertThat(expiredCoupons).isEmpty();
            
            // when - EXPIRED 상태만 제외
            expiredCoupons = couponRepository.findExpiredCouponsNotInStatus(
                    now, CouponStatus.EXPIRED
            );
            
            // then - ACTIVE 상태의 만료된 쿠폰이 조회됨
            assertThat(expiredCoupons).hasSize(1);
            assertThat(expiredCoupons.get(0).getCode()).isEqualTo("EXPIRED_ACTIVE");
        }
    }

    private static Stream<Arguments> provideCouponData() {
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
                Arguments.of(-999L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideEdgeCaseDiscountRates() {
        return Stream.of(
                Arguments.of("ZERO", "0"),
                Arguments.of("SMALL", "0.01"),
                Arguments.of("FULL", "1.00"),
                Arguments.of("EXCESSIVE", "2.00")
        );
    }
}