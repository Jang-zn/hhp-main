package kr.hhplus.be.server.unit.adapter.storage.inmemory.coupon;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponHistoryRepository;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static kr.hhplus.be.server.util.TestAssertions.CouponHistoryAssertions;
import static kr.hhplus.be.server.util.TestAssertions.CommonAssertions;

/**
 * InMemoryCouponHistoryRepository 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 발급 이력 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 쿠폰 발급 및 사용 시나리오를 반영한 테스트로 구성
 */
@DisplayName("쿠폰 히스토리 저장소 비즈니스 시나리오")
class InMemoryCouponHistoryRepositoryTest {

    private InMemoryCouponHistoryRepository couponHistoryRepository;

    @BeforeEach
    void setUp() {
        couponHistoryRepository = new InMemoryCouponHistoryRepository();
        couponHistoryRepository.clear();
    }

    @Test
    @DisplayName("고객의 쿠폰 발급 이력을 저장할 수 있다")
    void canSaveCustomerCouponIssuanceHistory() {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory().build();
        
        // When
        CouponHistory saved = couponHistoryRepository.save(history);
        
        // Then
        CouponHistoryAssertions.assertSavedCorrectly(saved, history);
        CouponHistoryAssertions.assertCouponIssued(saved);
    }

    @ParameterizedTest
    @MethodSource("provideDiverseHistoryData")
    @DisplayName("다양한 고객과 쿠폰의 발급 이력을 저장할 수 있다")
    void canSaveDiverseIssuanceHistory(Long userId, Long couponId, CouponHistoryStatus status) {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .userId(userId)
            .couponId(couponId)
            .status(status)
            .build();
        
        // When
        CouponHistory saved = couponHistoryRepository.save(history);
        
        // Then
        CouponHistoryAssertions.assertSavedCorrectly(saved, history);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCouponId()).isEqualTo(couponId);
        assertThat(saved.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("쿠폰 사용 이력을 저장할 수 있다")
    void canSaveCouponUsageHistory() {
        // Given
        CouponHistory usedHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory().build();
        
        // When
        CouponHistory saved = couponHistoryRepository.save(usedHistory);
        
        // Then
        CouponHistoryAssertions.assertCouponUsed(saved);
    }

    @Test
    @DisplayName("null 히스토리 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingNullHistory() {
        // When & Then
        assertThatThrownBy(() -> couponHistoryRepository.save(null))
            .isInstanceOf(CouponException.InvalidCouponHistoryData.class);
    }

    @Test
    @DisplayName("고객이 특정 쿠폰을 보유하고 있는지 확인할 수 있다")
    void canCheckIfCustomerOwnsCoupon() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .userId(userId)
            .couponId(couponId)
            .build();
        couponHistoryRepository.save(history);

        // When
        boolean exists = couponHistoryRepository.existsByUserIdAndCouponId(userId, couponId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("보유하지 않은 쿠폰 확인 시 false를 반환한다")
    void returnsFalseWhenCustomerDoesNotOwnCoupon() {
        // When
        boolean exists = couponHistoryRepository.existsByUserIdAndCouponId(999L, 999L);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("고객이 보유한 쿠폰 이력을 ID로 조회할 수 있다")
    void canFindCustomerCouponHistoryById() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .userId(userId)
            .couponId(couponId)
            .build();
        CouponHistory saved = couponHistoryRepository.save(history);

        // When
        Optional<CouponHistory> found = couponHistoryRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        CouponHistoryAssertions.assertSavedCorrectly(found.get(), history);
    }

    @Test
    @DisplayName("존재하지 않는 이력 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenHistoryNotFound() {
        // When
        Optional<CouponHistory> found = couponHistoryRepository.findById(999L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("고객의 모든 쿠폰 이력을 페이지네이션으로 조회할 수 있다")
    void canFindAllCustomerCouponHistoryWithPagination() {
        // Given
        Long userId = 1L;
        CouponHistory history1 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(null).userId(userId).couponId(1L).build();
        CouponHistory history2 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(null).userId(userId).couponId(2L).build();
        
        couponHistoryRepository.save(history1);
        couponHistoryRepository.save(history2);

        // When
        List<CouponHistory> histories = couponHistoryRepository.findByUserIdWithPagination(userId, 10, 0);

        // Then
        assertThat(histories).hasSize(2);
        CommonAssertions.assertListNotEmpty(histories);
    }

    @Test
    @DisplayName("쿠폰 이력이 없는 고객은 빈 목록을 받는다")
    void returnsEmptyListForCustomerWithNoHistory() {
        // When
        List<CouponHistory> histories = couponHistoryRepository.findByUserIdWithPagination(999L, 10, 0);

        // Then
        assertThat(histories).isEmpty();
    }

    @Test
    @DisplayName("고객의 특정 상태 쿠폰 이력을 조회할 수 있다")
    void canFindCustomerCouponHistoryByStatus() {
        // Given
        Long userId = 1L;
        CouponHistory issuedHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(null).userId(userId).couponId(1L).build();
        CouponHistory usedHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory()
            .id(null).userId(userId).couponId(2L).build();
        
        couponHistoryRepository.save(issuedHistory);
        couponHistoryRepository.save(usedHistory);

        // When
        List<CouponHistory> issuedHistories = couponHistoryRepository.findByUserIdAndStatus(userId, CouponHistoryStatus.ISSUED);

        // Then
        assertThat(issuedHistories).hasSize(1);
        CouponHistoryAssertions.assertCouponIssued(issuedHistories.get(0));
    }

    @Test
    @DisplayName("서로 다른 고객의 쿠폰 발급을 동시에 처리할 수 있다")
    void canHandleConcurrentIssuancesToDifferentCustomers() {
        // Given
        int numberOfCustomers = 10;
        Long couponId = 1L;

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfCustomers, () -> {
                CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                    .userId(System.nanoTime() % 10000) // 고유 사용자 ID
                    .couponId(couponId)
                    .build();
                return couponHistoryRepository.save(history);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfCustomers);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 고객의 서로 다른 쿠폰 발급을 동시에 처리할 수 있다")
    void canHandleConcurrentIssuancesToSameCustomer() {
        // Given
        Long userId = 1L;
        int numberOfCoupons = 5;

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfCoupons, () -> {
                CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                    .id(null)
                    .userId(userId)
                    .couponId(System.nanoTime() % 10000) // 고유 쿠폰 ID
                    .build();
                return couponHistoryRepository.save(history);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfCoupons);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        List<CouponHistory> userHistories = couponHistoryRepository.findByUserIdWithPagination(userId, 100, 0);
        assertThat(userHistories.size()).isEqualTo(numberOfCoupons);
    }

    @Test
    @DisplayName("쿠폰 히스토리 조회와 저장이 동시에 실행될 수 있다")
    void canReadAndWriteHistoryConcurrently() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        CouponHistory initialHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .userId(userId)
            .couponId(couponId)
            .build();
        couponHistoryRepository.save(initialHistory);

        // When - 읽기와 쓰기 작업을 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeMultipleTasks(List.of(
                // 읽기 작업
                () -> {
                    for (int i = 0; i < 20; i++) {
                        couponHistoryRepository.existsByUserIdAndCouponId(userId, couponId);
                        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                },
                // 쓰기 작업
                () -> {
                    for (int i = 0; i < 10; i++) {
                        CouponHistory newHistory = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                            .id(null)
                            .userId(userId + i)
                            .couponId(couponId + i)
                            .build();
                        couponHistoryRepository.save(newHistory);
                        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            ));

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("고객의 사용 가능한 쿠폰 개수를 조회할 수 있다")
    void canCountUsableCouponsByCustomer() {
        // Given
        Long userId = 1L;
        CouponHistory issuedHistory1 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(null).userId(userId).couponId(1L).build();
        CouponHistory issuedHistory2 = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
            .id(null).userId(userId).couponId(2L).build();
        CouponHistory usedHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory()
            .id(null).userId(userId).couponId(3L).build();
        
        couponHistoryRepository.save(issuedHistory1);
        couponHistoryRepository.save(issuedHistory2);
        couponHistoryRepository.save(usedHistory);

        // When
        long usableCount = couponHistoryRepository.countUsableCouponsByUserId(userId);

        // Then
        assertThat(usableCount).isEqualTo(2L);
    }

    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideDiverseHistoryData() {
        return Stream.of(
            Arguments.of(1L, 1L, CouponHistoryStatus.ISSUED),
            Arguments.of(2L, 2L, CouponHistoryStatus.USED),
            Arguments.of(3L, 1L, CouponHistoryStatus.ISSUED)
        );
    }
}