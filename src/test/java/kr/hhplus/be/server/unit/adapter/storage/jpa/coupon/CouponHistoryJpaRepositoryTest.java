package kr.hhplus.be.server.unit.adapter.storage.jpa.coupon;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.CouponHistoryJpaRepository;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("CouponHistoryJpaRepository 단위 테스트")
class CouponHistoryJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<CouponHistory> couponHistoryQuery;

    @Mock
    private TypedQuery<Long> countQuery;

    private CouponHistoryJpaRepository couponHistoryJpaRepository;

    @BeforeEach
    void setUp() {
        couponHistoryJpaRepository = new CouponHistoryJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("쿠폰 히스토리 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 쿠폰 히스토리 저장 (persist)")
        void save_NewCouponHistory_Success() {
            // given
            User user = User.builder().id(1L).name("테스트 사용자").build();
            Coupon coupon = Coupon.builder().id(1L).code("TEST_COUPON").build();
            CouponHistory couponHistory = CouponHistory.builder()
                    .user(user)
                    .coupon(coupon)
                    .status(CouponHistoryStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(couponHistory);

            // when
            CouponHistory savedHistory = couponHistoryJpaRepository.save(couponHistory);

            // then
            assertThat(savedHistory).isEqualTo(couponHistory);
            verify(entityManager, times(1)).persist(couponHistory);
            verify(entityManager, never()).merge(any());
        }

        @Test
        @DisplayName("성공케이스: 기존 쿠폰 히스토리 업데이트 (merge)")
        void save_ExistingCouponHistory_Success() {
            // given
            User user = User.builder().id(1L).name("테스트 사용자").build();
            Coupon coupon = Coupon.builder().id(1L).code("TEST_COUPON").build();
            CouponHistory couponHistory = CouponHistory.builder()
                    .id(1L)
                    .user(user)
                    .coupon(coupon)
                    .status(CouponHistoryStatus.USED)
                    .issuedAt(LocalDateTime.now())
                    .usedAt(LocalDateTime.now())
                    .build();

            when(entityManager.merge(couponHistory)).thenReturn(couponHistory);

            // when
            CouponHistory savedHistory = couponHistoryJpaRepository.save(couponHistory);

            // then
            assertThat(savedHistory).isEqualTo(couponHistory);
            verify(entityManager, times(1)).merge(couponHistory);
            verify(entityManager, never()).persist(any());
        }

        @ParameterizedTest
        @EnumSource(CouponHistoryStatus.class)
        @DisplayName("성공케이스: 다양한 상태의 쿠폰 히스토리 저장")
        void save_WithDifferentStatuses(CouponHistoryStatus status) {
            // given
            User user = User.builder().id(1L).build();
            Coupon coupon = Coupon.builder().id(1L).build();
            CouponHistory couponHistory = CouponHistory.builder()
                    .user(user)
                    .coupon(coupon)
                    .status(status)
                    .issuedAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(couponHistory);

            // when
            CouponHistory savedHistory = couponHistoryJpaRepository.save(couponHistory);

            // then
            assertThat(savedHistory.getStatus()).isEqualTo(status);
            verify(entityManager, times(1)).persist(couponHistory);
        }
    }

    @Nested
    @DisplayName("존재 여부 확인 테스트")
    class ExistsByUserAndCouponTests {

        @Test
        @DisplayName("성공케이스: 사용자와 쿠폰으로 존재하는 히스토리 확인")
        void existsByUserAndCoupon_Exists() {
            // given
            User user = User.builder().id(1L).build();
            Coupon coupon = Coupon.builder().id(1L).build();

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("user", user)).thenReturn(countQuery);
            when(countQuery.setParameter("coupon", coupon)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(1L);

            // when
            boolean exists = couponHistoryJpaRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("성공케이스: 사용자와 쿠폰으로 존재하지 않는 히스토리 확인")
        void existsByUserAndCoupon_NotExists() {
            // given
            User user = User.builder().id(1L).build();
            Coupon coupon = Coupon.builder().id(1L).build();

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("user", user)).thenReturn(countQuery);
            when(countQuery.setParameter("coupon", coupon)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            // when
            boolean exists = couponHistoryJpaRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 쿠폰 히스토리 조회")
        void findById_Success() {
            // given
            Long id = 1L;
            CouponHistory expectedHistory = CouponHistory.builder()
                    .id(id)
                    .status(CouponHistoryStatus.ISSUED)
                    .build();

            when(entityManager.find(CouponHistory.class, id)).thenReturn(expectedHistory);

            // when
            Optional<CouponHistory> foundHistory = couponHistoryJpaRepository.findById(id);

            // then
            assertThat(foundHistory).isPresent();
            assertThat(foundHistory.get()).isEqualTo(expectedHistory);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long id = 999L;
            when(entityManager.find(CouponHistory.class, id)).thenReturn(null);

            // when
            Optional<CouponHistory> foundHistory = couponHistoryJpaRepository.findById(id);

            // then
            assertThat(foundHistory).isEmpty();
        }
    }

    @Nested
    @DisplayName("페이징 조회 테스트")
    class FindByUserWithPaginationTests {

        @Test
        @DisplayName("성공케이스: 사용자별 페이징 조회")
        void findByUserWithPagination_Success() {
            // given
            User user = User.builder().id(1L).build();
            int limit = 10;
            int offset = 0;
            
            List<CouponHistory> expectedHistories = createCouponHistories(5);

            when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("user", user)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setMaxResults(limit)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setFirstResult(offset)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

            // when
            List<CouponHistory> histories = couponHistoryJpaRepository.findByUserWithPagination(user, limit, offset);

            // then
            assertThat(histories).hasSize(5);
            assertThat(histories).isEqualTo(expectedHistories);
        }

        @Test
        @DisplayName("성공케이스: 빈 결과 페이징 조회")
        void findByUserWithPagination_EmptyResult() {
            // given
            User user = User.builder().id(999L).build();
            int limit = 10;
            int offset = 0;

            when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("user", user)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setMaxResults(limit)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setFirstResult(offset)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.getResultList()).thenReturn(new ArrayList<>());

            // when
            List<CouponHistory> histories = couponHistoryJpaRepository.findByUserWithPagination(user, limit, offset);

            // then
            assertThat(histories).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태별 조회 테스트")
    class FindByUserAndStatusTests {

        @ParameterizedTest
        @EnumSource(CouponHistoryStatus.class)
        @DisplayName("성공케이스: 사용자와 상태로 쿠폰 히스토리 조회")
        void findByUserAndStatus_Success(CouponHistoryStatus status) {
            // given
            User user = User.builder().id(1L).build();
            List<CouponHistory> expectedHistories = createCouponHistoriesWithStatus(3, status);

            when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("user", user)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("status", status)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

            // when
            List<CouponHistory> histories = couponHistoryJpaRepository.findByUserAndStatus(user, status);

            // then
            assertThat(histories).hasSize(3);
            assertThat(histories).allMatch(h -> h.getStatus() == status);
        }
    }

    @Nested
    @DisplayName("만료된 쿠폰 조회 테스트")
    class FindExpiredHistoriesTests {

        @Test
        @DisplayName("성공케이스: 만료된 특정 상태 쿠폰 히스토리 조회")
        void findExpiredHistoriesInStatus_Success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponHistoryStatus status = CouponHistoryStatus.ISSUED;
            List<CouponHistory> expectedHistories = createExpiredCouponHistories(2, status, now);

            when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("now", now)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.setParameter("status", status)).thenReturn(couponHistoryQuery);
            when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

            // when
            List<CouponHistory> histories = couponHistoryJpaRepository.findExpiredHistoriesInStatus(now, status);

            // then
            assertThat(histories).hasSize(2);
            // CouponHistory에는 expiresAt이 없고 coupon.endDate를 참조함
            assertThat(histories).allMatch(h -> h.getStatus() == status);
        }
    }

    @Nested
    @DisplayName("사용 가능한 쿠폰 개수 조회 테스트")
    class CountUsableCouponsTests {

        @Test
        @DisplayName("성공케이스: 사용자의 사용 가능한 쿠폰 개수 조회")
        void countUsableCouponsByUser_Success() {
            // given
            User user = User.builder().id(1L).build();
            long expectedCount = 5L;

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("user", user)).thenReturn(countQuery);
            when(countQuery.setParameter("status", CouponHistoryStatus.ISSUED)).thenReturn(countQuery);
            when(countQuery.setParameter(eq("now"), any(LocalDateTime.class))).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(expectedCount);

            // when
            long count = couponHistoryJpaRepository.countUsableCouponsByUser(user);

            // then
            assertThat(count).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("성공케이스: 사용 가능한 쿠폰이 없는 경우")
        void countUsableCouponsByUser_NoUsableCoupons() {
            // given
            User user = User.builder().id(1L).build();

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("user", user)).thenReturn(countQuery);
            when(countQuery.setParameter("status", CouponHistoryStatus.ISSUED)).thenReturn(countQuery);
            when(countQuery.setParameter(eq("now"), any(LocalDateTime.class))).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            // when
            long count = couponHistoryJpaRepository.countUsableCouponsByUser(user);

            // then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 쿠폰 히스토리 동시 저장")
        void save_ConcurrentSaveForSameUser() throws Exception {
            // given
            User user = User.builder().id(100L).build();
            Coupon coupon = Coupon.builder().id(1L).build();

            int numberOfThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(entityManager).persist(any(CouponHistory.class));

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when
            for (int i = 0; i < numberOfThreads; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        CouponHistory history = CouponHistory.builder()
                                .user(user)
                                .coupon(coupon)
                                .status(CouponHistoryStatus.ISSUED)
                                .issuedAt(LocalDateTime.now())
                                .build();
                        couponHistoryJpaRepository.save(history);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(numberOfThreads);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: EntityManager persist 예외")
        void save_PersistException() {
            // given
            CouponHistory history = CouponHistory.builder()
                    .user(User.builder().id(1L).build())
                    .coupon(Coupon.builder().id(1L).build())
                    .status(CouponHistoryStatus.ISSUED)
                    .build();

            doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(history);

            // when & then
            assertThatThrownBy(() -> couponHistoryJpaRepository.save(history))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 연결 실패");
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long id = 1L;
            when(entityManager.find(CouponHistory.class, id))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<CouponHistory> result = couponHistoryJpaRepository.findById(id);

            // then
            assertThat(result).isEmpty();
        }
    }

    private List<CouponHistory> createCouponHistories(int count) {
        List<CouponHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            histories.add(CouponHistory.builder()
                    .id((long) (i + 1))
                    .status(CouponHistoryStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .build());
        }
        return histories;
    }

    private List<CouponHistory> createCouponHistoriesWithStatus(int count, CouponHistoryStatus status) {
        List<CouponHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            histories.add(CouponHistory.builder()
                    .id((long) (i + 1))
                    .status(status)
                    .issuedAt(LocalDateTime.now())
                    .build());
        }
        return histories;
    }

    private List<CouponHistory> createExpiredCouponHistories(int count, CouponHistoryStatus status, LocalDateTime now) {
        List<CouponHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Coupon expiredCoupon = Coupon.builder()
                    .id((long) (i + 1))
                    .endDate(now.minusDays(1))
                    .build();
            histories.add(CouponHistory.builder()
                    .id((long) (i + 1))
                    .coupon(expiredCoupon)
                    .status(status)
                    .issuedAt(now.minusDays(60))
                    .build());
        }
        return histories;
    }
}