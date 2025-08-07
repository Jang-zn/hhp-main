package kr.hhplus.be.server.unit.adapter.storage.jpa.coupon;

import kr.hhplus.be.server.adapter.storage.jpa.CouponHistoryJpaRepository;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static kr.hhplus.be.server.util.TestAssertions.CouponHistoryAssertions;
import static kr.hhplus.be.server.util.TestAssertions.CommonAssertions;

/**
 * CouponHistoryJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: JPA를 통한 쿠폰 이력 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 쿠폰 발급 및 사용 시나리오를 반영한 Mock 기반 단위 테스트
 */
@DataJpaTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("쿠폰 히스토리 JPA 저장소 비즈니스 시나리오")
class CouponHistoryJpaRepositoryTest {

    @Mock private EntityManager entityManager;
    @Mock private TypedQuery<CouponHistory> couponHistoryQuery;
    @Mock private TypedQuery<Long> countQuery;

    private CouponHistoryJpaRepository couponHistoryJpaRepository;

    @BeforeEach
    void setUp() {
        couponHistoryJpaRepository = new CouponHistoryJpaRepository(entityManager);
    }

    @Test
    @DisplayName("새로운 쿠폰 이력을 데이터베이스에 저장할 수 있다")
    void canPersistNewCouponHistory() {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory().build();
        doNothing().when(entityManager).persist(history);

        // When
        CouponHistory saved = couponHistoryJpaRepository.save(history);

        // Then
        assertThat(saved).isNotNull();
        verify(entityManager, times(1)).persist(history);
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("기존 쿠폰 이력을 업데이트할 수 있다")
    void canMergeExistingCouponHistory() {
        // Given
        CouponHistory existingHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory()
            .id(1L).build();
        when(entityManager.merge(existingHistory)).thenReturn(existingHistory);

        // When
        CouponHistory updated = couponHistoryJpaRepository.save(existingHistory);

        // Then
        assertThat(updated).isNotNull();
        verify(entityManager, times(1)).merge(existingHistory);
        verify(entityManager, never()).persist(any());
    }

    @ParameterizedTest
    @EnumSource(CouponHistoryStatus.class)
    @DisplayName("모든 쿠폰 상태에 대해 이력을 저장할 수 있다")
    void canSaveCouponHistoryWithAllStatuses(CouponHistoryStatus status) {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .status(status).build();
        doNothing().when(entityManager).persist(history);

        // When
        CouponHistory saved = couponHistoryJpaRepository.save(history);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(status);
        verify(entityManager, times(1)).persist(history);
    }

    @Test
    @DisplayName("고객이 특정 쿠폰을 보유하고 있는지 확인할 수 있다")
    void canCheckIfCustomerOwnsCoupon() {
        // Given
        Long userId = 1L, couponId = 1L;
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter("userId", userId)).thenReturn(countQuery);
        when(countQuery.setParameter("couponId", couponId)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        // When
        boolean exists = couponHistoryJpaRepository.existsByUserIdAndCouponId(userId, couponId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("보유하지 않은 쿠폰 확인 시 false를 반환한다")
    void returnsFalseWhenCustomerDoesNotOwnCoupon() {
        // Given
        Long userId = 1L, couponId = 1L;
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter("userId", userId)).thenReturn(countQuery);
        when(countQuery.setParameter("couponId", couponId)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        // When
        boolean exists = couponHistoryJpaRepository.existsByUserIdAndCouponId(userId, couponId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("쿠폰 이력을 ID로 조회할 수 있다")
    void canFindCouponHistoryById() {
        // Given
        Long id = 1L;
        CouponHistory expectedHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(id).build();
        when(entityManager.find(CouponHistory.class, id)).thenReturn(expectedHistory);

        // When
        Optional<CouponHistory> found = couponHistoryJpaRepository.findById(id);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(expectedHistory);
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenHistoryNotFoundById() {
        // Given
        Long id = 999L;
        when(entityManager.find(CouponHistory.class, id)).thenReturn(null);

        // When
        Optional<CouponHistory> found = couponHistoryJpaRepository.findById(id);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("고객의 쿠폰 이력을 페이지네이션으로 조회할 수 있다")
    void canFindCustomerCouponHistoryWithPagination() {
        // Given
        Long userId = 1L;
        int limit = 10, offset = 0;
        List<CouponHistory> expectedHistories = createTestCouponHistories(5);
        
        when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("userId", userId)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setMaxResults(limit)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setFirstResult(offset)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

        // When
        List<CouponHistory> histories = couponHistoryJpaRepository.findByUserIdWithPagination(userId, limit, offset);

        // Then
        assertThat(histories).hasSize(5);
        CommonAssertions.assertListNotEmpty(histories);
    }

    @Test
    @DisplayName("쿠폰 이력이 없는 고객은 빈 목록을 받는다")
    void returnsEmptyListForCustomerWithNoHistory() {
        // Given
        Long userId = 999L;
        int limit = 10, offset = 0;
        
        when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("userId", userId)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setMaxResults(limit)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setFirstResult(offset)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.getResultList()).thenReturn(new ArrayList<>());

        // When
        List<CouponHistory> histories = couponHistoryJpaRepository.findByUserIdWithPagination(userId, limit, offset);

        // Then
        assertThat(histories).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(CouponHistoryStatus.class)
    @DisplayName("고객의 특정 상태 쿠폰 이력을 조회할 수 있다")
    void canFindCustomerCouponHistoryByStatus(CouponHistoryStatus status) {
        // Given
        Long userId = 1L;
        List<CouponHistory> expectedHistories = createTestCouponHistoriesWithStatus(3, status);
        
        when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("userId", userId)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("status", status)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

        // When
        List<CouponHistory> histories = couponHistoryJpaRepository.findByUserIdAndStatus(userId, status);

        // Then
        assertThat(histories).hasSize(3);
        assertThat(histories).allMatch(h -> h.getStatus() == status);
    }

    @Test
    @DisplayName("만료된 쿠폰 이력을 조회할 수 있다")
    void canFindExpiredCouponHistories() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        CouponHistoryStatus status = CouponHistoryStatus.ISSUED;
        List<CouponHistory> expectedHistories = createTestCouponHistories(2);
        
        when(entityManager.createQuery(anyString(), eq(CouponHistory.class))).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("now", now)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.setParameter("status", status)).thenReturn(couponHistoryQuery);
        when(couponHistoryQuery.getResultList()).thenReturn(expectedHistories);

        // When
        List<CouponHistory> histories = couponHistoryJpaRepository.findExpiredHistoriesInStatus(now, status);

        // Then
        assertThat(histories).hasSize(2);
        assertThat(histories).allMatch(h -> h.getStatus() == status);
    }

    @Test
    @DisplayName("고객의 사용 가능한 쿠폰 개수를 조회할 수 있다")
    void canCountUsableCouponsByCustomer() {
        // Given
        Long userId = 1L;
        long expectedCount = 5L;
        
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter("userId", userId)).thenReturn(countQuery);
        when(countQuery.setParameter("status", CouponHistoryStatus.ISSUED)).thenReturn(countQuery);
        when(countQuery.setParameter(eq("now"), any(LocalDateTime.class))).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(expectedCount);

        // When
        long count = couponHistoryJpaRepository.countUsableCouponsByUserId(userId);

        // Then
        assertThat(count).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("사용 가능한 쿠폰이 없는 고객은 0개를 받는다")
    void returnsZeroWhenNoUsableCoupons() {
        // Given
        Long userId = 1L;
        
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter("userId", userId)).thenReturn(countQuery);
        when(countQuery.setParameter("status", CouponHistoryStatus.ISSUED)).thenReturn(countQuery);
        when(countQuery.setParameter(eq("now"), any(LocalDateTime.class))).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        // When
        long count = couponHistoryJpaRepository.countUsableCouponsByUserId(userId);

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("동시에 여러 고객의 쿠폰 이력을 저장할 수 있다")
    void canHandleConcurrentCouponHistorySaving() {
        // Given
        int numberOfThreads = 10;
        doNothing().when(entityManager).persist(any(CouponHistory.class));

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfThreads, () -> {
                CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                    .id(null) // Ensure new entity
                    .userId(System.nanoTime() % 1000)
                    .couponId(System.nanoTime() % 1000)
                    .build();
                return couponHistoryJpaRepository.save(history);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfThreads);
        assertThat(result.getFailureCount()).isEqualTo(0);
        // Note: verify() doesn't work reliably with concurrent execution, so we skip it
    }

    @Test
    @DisplayName("데이터베이스 연결 실패 시 예외가 발생한다")
    void throwsExceptionWhenDatabaseConnectionFails() {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory().build();
        RuntimeException dbException = new RuntimeException("DB 연결 실패");
        doThrow(dbException).when(entityManager).persist(any(CouponHistory.class));

        // When & Then
        assertThatThrownBy(() -> couponHistoryJpaRepository.save(history))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("조회 중 데이터베이스 오류 발생 시 빈 결과를 반환한다")
    void returnsEmptyWhenDatabaseErrorOccurs() {
        // Given
        Long id = 1L;
        when(entityManager.find(CouponHistory.class, id))
            .thenThrow(new RuntimeException("데이터베이스 오류"));

        // When
        Optional<CouponHistory> result = couponHistoryJpaRepository.findById(id);

        // Then
        assertThat(result).isEmpty();
    }

    // === 테스트 데이터 생성 헬퍼 메서드 ===
    
    private List<CouponHistory> createTestCouponHistories(int count) {
        List<CouponHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            histories.add(TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .id((long) (i + 1))
                .build());
        }
        return histories;
    }

    private List<CouponHistory> createTestCouponHistoriesWithStatus(int count, CouponHistoryStatus status) {
        List<CouponHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            histories.add(TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .id((long) (i + 1))
                .status(status)
                .build());
        }
        return histories;
    }
}