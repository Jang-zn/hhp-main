package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponHistoryRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryCouponHistoryRepository 단위 테스트")
class InMemoryCouponHistoryRepositoryTest {

    private InMemoryCouponHistoryRepository couponHistoryRepository;

    @BeforeEach
    void setUp() {
        couponHistoryRepository = new InMemoryCouponHistoryRepository();
    }

    @Nested
    @DisplayName("쿠폰 히스토리 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 쿠폰 히스토리 저장")
        void save_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(1)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        CouponHistory couponHistory = CouponHistory.builder()
                .id(1L)
                .user(user)
                .coupon(coupon)
                .issuedAt(LocalDateTime.now())
                .build();

        // when
        CouponHistory savedHistory = couponHistoryRepository.save(couponHistory);

        // then
        assertThat(savedHistory).isNotNull();
        assertThat(savedHistory.getUser()).isEqualTo(user);
        assertThat(savedHistory.getCoupon()).isEqualTo(coupon);
        assertThat(savedHistory.getIssuedAt()).isNotNull();
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryCouponHistoryRepositoryTest#provideCouponHistoryData")
        @DisplayName("성공케이스: 다양한 쿠폰 히스토리 데이터로 저장")
        void save_WithDifferentHistoryData(String userName, String couponCode) {
            // given
            User user = User.builder()
                    .id(2L)
                    .name(userName)
                    .build();
            
            Coupon coupon = Coupon.builder()
                    .id(2L)
                    .code(couponCode)
                    .discountRate(new BigDecimal("0.15"))
                    .maxIssuance(200)
                    .issuedCount(1)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .build();
            
            CouponHistory couponHistory = CouponHistory.builder()
                    .id(2L)
                    .user(user)
                    .coupon(coupon)
                    .issuedAt(LocalDateTime.now())
                    .build();

            // when
            CouponHistory savedHistory = couponHistoryRepository.save(couponHistory);

            // then
            assertThat(savedHistory).isNotNull();
            assertThat(savedHistory.getUser().getName()).isEqualTo(userName);
            assertThat(savedHistory.getCoupon().getCode()).isEqualTo(couponCode);
        }
    }

    @Nested
    @DisplayName("쿠폰 히스토리 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 사용자 ID와 쿠폰 ID로 히스토리 존재 여부 확인")
        void existsByUserAndCoupon_Success() {
        // given
        User user = User.builder().id(1L).name("테스트 사용자").build();
        Coupon coupon = Coupon.builder().id(1L).code("SALE20").discountRate(new BigDecimal("0.20")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        CouponHistory couponHistory = CouponHistory.builder().id(1L).user(user).coupon(coupon).issuedAt(LocalDateTime.now()).build();
        couponHistoryRepository.save(couponHistory);

        // when
        boolean exists = couponHistoryRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("성공케이스: 사용자 ID로 쿠폰 히스토리 목록 조회")
        void findByUserWithPagination_Success() {
        // given
        User user = User.builder().id(1L).name("테스트 사용자").build();
        Coupon coupon1 = Coupon.builder().id(1L).code("SALE10").discountRate(new BigDecimal("0.10")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        Coupon coupon2 = Coupon.builder().id(2L).code("SALE20").discountRate(new BigDecimal("0.20")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        couponHistoryRepository.save(CouponHistory.builder().id(1L).user(user).coupon(coupon1).issuedAt(LocalDateTime.now()).build());
        couponHistoryRepository.save(CouponHistory.builder().id(2L).user(user).coupon(coupon2).issuedAt(LocalDateTime.now()).build());

        // when
        List<CouponHistory> histories = couponHistoryRepository.findByUserWithPagination(user, 1, 0);

            // then
            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).getCoupon().getCode()).isEqualTo("SALE10");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 쿠폰 히스토리 조회")
        void existsByUserAndCoupon_NotFound() {
            // given
            User user = User.builder().id(999L).name("비존재 사용자").build();
            Coupon coupon = Coupon.builder().id(999L).code("NOTFOUND").discountRate(new BigDecimal("0.10")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();

            // when
            boolean exists = couponHistoryRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("실패케이스: 빈 페이지 조회")
        void findByUserWithPagination_EmptyResult() {
            // given
            User user = User.builder().id(999L).name("데이터 없는 사용자").build();

            // when
            List<CouponHistory> histories = couponHistoryRepository.findByUserWithPagination(user, 10, 0);

            // then
            assertThat(histories).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 쿠폰 히스토리 동시 저장")
        void save_ConcurrentSaveForDifferentHistories() throws Exception {
            // given
            int numberOfHistories = 20;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfHistories);
            AtomicInteger successCount = new AtomicInteger(0);

            // when - 서로 다른 쿠폰 히스토리들을 동시에 저장
            for (int i = 0; i < numberOfHistories; i++) {
                final int historyIndex = i + 1;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .id((long) historyIndex)
                                .name("사용자" + historyIndex)
                                .build();
                        
                        Coupon coupon = Coupon.builder()
                                .id((long) historyIndex)
                                .code("CONCURRENT" + historyIndex)
                                .discountRate(new BigDecimal("0.10"))
                                .maxIssuance(100)
                                .issuedCount(0)
                                .startDate(LocalDateTime.now())
                                .endDate(LocalDateTime.now().plusDays(30))
                                .build();
                        
                        CouponHistory history = CouponHistory.builder()
                                .id((long) historyIndex)
                                .user(user)
                                .coupon(coupon)
                                .issuedAt(LocalDateTime.now())
                                .build();
                        
                        couponHistoryRepository.save(history);
                        successCount.incrementAndGet();
                        Thread.sleep(1);
                    } catch (Exception e) {
                        System.err.println("Error for history " + historyIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // then - 모든 히스토리가 성공적으로 저장되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfHistories);
            
            // 각 사용자-쿠폰 조합이 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfHistories; i++) {
                User user = User.builder().id((long) i).name("사용자" + i).build();
                Coupon coupon = Coupon.builder().id((long) i).code("CONCURRENT" + i).build();
                boolean exists = couponHistoryRepository.existsByUserAndCoupon(user, coupon);
                assertThat(exists).isTrue();
            }
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 여러 쿠폰 동시 발급")
        void save_ConcurrentIssuanceForSameUser() throws Exception {
            // given
            Long userId = 500L;
            User user = User.builder()
                    .id(userId)
                    .name("동시성 테스트 사용자")
                    .build();

            int numberOfCoupons = 10;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfCoupons);
            AtomicInteger successfulIssuances = new AtomicInteger(0);

            // when - 동일한 사용자에게 여러 쿠폰을 동시에 발급
            for (int i = 0; i < numberOfCoupons; i++) {
                final int couponIndex = i + 1;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Coupon coupon = Coupon.builder()
                                .id((long) couponIndex)
                                .code("MULTI_COUPON" + couponIndex)
                                .discountRate(new BigDecimal("0.15"))
                                .maxIssuance(1000)
                                .issuedCount(0)
                                .startDate(LocalDateTime.now())
                                .endDate(LocalDateTime.now().plusDays(30))
                                .build();
                        
                        CouponHistory history = CouponHistory.builder()
                                .id((long) (500 + couponIndex))
                                .user(user)
                                .coupon(coupon)
                                .issuedAt(LocalDateTime.now())
                                .build();
                        
                        couponHistoryRepository.save(history);
                        successfulIssuances.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Issuance error for coupon " + couponIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // then
            assertThat(successfulIssuances.get()).isEqualTo(numberOfCoupons);
            
            // 사용자의 쿠폰 히스토리 확인
            List<CouponHistory> userHistories = couponHistoryRepository.findByUserWithPagination(user, numberOfCoupons, 0);
            assertThat(userHistories).hasSize(numberOfCoupons);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            User testUser = User.builder()
                    .id(600L)
                    .name("읽기쓰기 테스트 사용자")
                    .build();
            
            Coupon baseCoupon = Coupon.builder()
                    .id(600L)
                    .code("READ_WRITE_TEST")
                    .discountRate(new BigDecimal("0.20"))
                    .maxIssuance(500)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .build();
            
            // 초기 히스토리 생성
            CouponHistory initialHistory = CouponHistory.builder()
                    .id(600L)
                    .user(testUser)
                    .coupon(baseCoupon)
                    .issuedAt(LocalDateTime.now())
                    .build();
            couponHistoryRepository.save(initialHistory);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
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
                            boolean exists = couponHistoryRepository.existsByUserAndCoupon(testUser, baseCoupon);
                            if (exists) {
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
                            Coupon newCoupon = Coupon.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .code("WRITE_TEST" + writerId + "_" + j)
                                    .discountRate(new BigDecimal("0.10"))
                                    .maxIssuance(100)
                                    .issuedCount(0)
                                    .startDate(LocalDateTime.now())
                                    .endDate(LocalDateTime.now().plusDays(30))
                                    .build();
                            
                            CouponHistory newHistory = CouponHistory.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .user(testUser)
                                    .coupon(newCoupon)
                                    .issuedAt(LocalDateTime.now())
                                    .build();
                            
                            couponHistoryRepository.save(newHistory);
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
            List<CouponHistory> finalHistories = couponHistoryRepository.findByUserWithPagination(testUser, 200, 0);
            assertThat(finalHistories.size()).isGreaterThan(1);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> provideCouponHistoryData() {
        return Stream.of(
                Arguments.of("홍길동", "WELCOME10"),
                Arguments.of("김철수", "SUMMER25"),
                Arguments.of("이영희", "VIP30")
        );
    }
}