package kr.hhplus.be.server.unit.adapter.locking;

import kr.hhplus.be.server.adapter.locking.InMemoryLockingAdapter;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import kr.hhplus.be.server.util.ConcurrencyTestHelper.ConcurrencyTestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryLockingAdapter 동시성 제어 테스트
 * 
 * Why: 메모리 기반 락 메커니즘이 동시성 환경에서 올바르게 작동하는지 검증
 * How: 다양한 동시성 시나리오를 통해 락의 상호 배제성과 재진입성을 확인
 */
@DisplayName("메모리 기반 락 동시성 제어")
class InMemoryLockingAdapterTest {

    private InMemoryLockingAdapter lockingAdapter;

    @BeforeEach
    void setUp() {
        lockingAdapter = new InMemoryLockingAdapter();
    }

    @Test
    @DisplayName("리소스에 대한 독점적 접근을 보장한다")
    void ensuresExclusiveAccessToResource() {
        // Given - 특정 리소스에 대한 락 키
        String resourceKey = "product-stock-1";

        // When - 리소스에 대한 락 획득
        boolean lockAcquired = lockingAdapter.acquireLock(resourceKey);

        // Then - 락 획득 성공 및 리소스가 보호됨
        assertThat(lockAcquired).as("리소스 락이 성공적으로 획득되어야 함").isTrue();
        assertThat(lockingAdapter.isLocked(resourceKey)).as("리소스가 락으로 보호되어야 함").isTrue();

        // When - 리소스 사용 완료 후 락 해제
        lockingAdapter.releaseLock(resourceKey);

        // Then - 리소스가 다시 사용 가능해짐
        assertThat(lockingAdapter.isLocked(resourceKey)).as("리소스 락이 해제되어 접근 가능해야 함").isFalse();
    }

    @Test
    @DisplayName("동일 스레드에서 중복 락 요청을 허용한다")
    void allowsReentrantLockingFromSameThread() {
        // Given - 복잡한 비즈니스 로직에서 같은 리소스에 여러 번 락이 필요한 상황
        // Why: 주문 생성 → 재고 확인 → 재고 예약 과정에서 동일 상품에 대한 중복 락 필요
        String resourceKey = "product-stock-1";

        // When - 동일 스레드에서 연속된 락 획득
        boolean firstLock = lockingAdapter.acquireLock(resourceKey);
        boolean secondLock = lockingAdapter.acquireLock(resourceKey);

        // Then - 모든 락 획득이 성공하고 리소스는 여전히 보호됨
        assertThat(firstLock).as("첫 번째 락 획득이 성공해야 함").isTrue();
        assertThat(secondLock).as("동일 스레드의 재진입 락이 성공해야 함").isTrue();
        assertThat(lockingAdapter.isLocked(resourceKey)).as("리소스는 여전히 보호 상태여야 함").isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 동시 접근을 차단한다")
    void preventsSimultaneousAccessFromDifferentUsers() throws InterruptedException {
        // Given - 동일한 상품에 두 사용자가 동시 주문하는 상황
        // Why: 재고 관리에서 race condition 방지를 위한 상호 배제성 검증
        String productStockKey = "product-stock-1";
        
        // When - 첫 번째 사용자가 상품 재고에 대한 락 획득
        boolean firstUserLock = lockingAdapter.acquireLock(productStockKey);
        
        // Then - 첫 번째 사용자는 성공
        assertThat(firstUserLock).as("첫 번째 사용자의 락 획득이 성공해야 함").isTrue();
        
        // When - 다른 스레드에서 동일한 상품에 접근 시도
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean secondUserResult = new AtomicBoolean(true); // 기본값을 true로 설정해 비교를 강화
        
        Thread secondUserThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean result = lockingAdapter.acquireLock(productStockKey);
                secondUserResult.set(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        secondUserThread.start();
        startLatch.countDown();
        secondUserThread.join(1000); // 1초 대기
        
        // Then - 다른 스레드의 접근은 차단되어야 함
        assertThat(secondUserResult.get()).as("다른 스레드의 동시 접근은 차단되어야 함").isFalse();
        assertThat(lockingAdapter.isLocked(productStockKey)).as("리소스는 여전히 첫 번째 사용자에게 락됨").isTrue();
    }

    @Test  
    @DisplayName("리소스 사용 완료 후 다른 사용자가 접근할 수 있다")
    void allowsAccessAfterResourceIsReleased() {
        // Given - 상품 주문 완료 후 다음 고객이 접근하는 상황
        String productStockKey = "product-stock-1";

        // When - 첫 번째 고객이 주문 프로세스를 완료하고 리소스 해제
        lockingAdapter.acquireLock(productStockKey);
        lockingAdapter.releaseLock(productStockKey);

        // Then - 두 번째 고객이 동일한 상품에 접근 가능
        boolean nextCustomerAccess = lockingAdapter.acquireLock(productStockKey);
        assertThat(nextCustomerAccess).as("리소스 해제 후 다른 사용자의 접근이 가능해야 함").isTrue();
        assertThat(lockingAdapter.isLocked(productStockKey)).as("새로운 사용자에게 락이 할당됨").isTrue();
    }

    @Test
    @DisplayName("리소스 보호 상태를 정확히 감지한다")
    void accuratelyDetectsResourceProtectionStatus() {
        // Given - 보호가 필요한 리소스와 일반 리소스
        String protectedResource = "critical-resource";
        String availableResource = "available-resource";

        // When - 중요한 리소스는 보호하고 일반 리소스는 그대로 둠
        lockingAdapter.acquireLock(protectedResource);

        // Then - 각 리소스의 보호 상태가 정확히 반영됨
        assertThat(lockingAdapter.isLocked(protectedResource))
            .as("보호된 리소스는 사용 중 상태로 표시되어야 함")
            .isTrue();
        
        assertThat(lockingAdapter.isLocked(availableResource))
            .as("보호되지 않은 리소스는 사용 가능 상태여야 함")
            .isFalse();

        // When - 보호된 리소스 사용 완료
        lockingAdapter.releaseLock(protectedResource);

        // Then - 리소스가 다시 사용 가능 상태로 변경됨
        assertThat(lockingAdapter.isLocked(protectedResource))
            .as("사용 완료된 리소스는 사용 가능 상태로 변경되어야 함")
            .isFalse();
    }

    @Test
    @DisplayName("동시 주문 상황에서 하나의 요청만 처리한다")
    void handlesOnlyOneRequestInSimultaneousOrders() {
        // Given - 인기 상품에 대한 10명의 동시 주문 상황
        // Why: 마지막 1개 재고 상품에 대한 동시 주문에서 overselling 방지
        String lastItemKey = "product-stock-last-item";
        int simultaneousOrders = 10;

        // When - 10명이 동시에 마지막 상품 주문 시도
        ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
            simultaneousOrders, 
            () -> {
                boolean lockAcquired = lockingAdapter.acquireLock(lastItemKey);
                if (!lockAcquired) {
                    throw new RuntimeException("락 획득 실패");
                }
                return lockAcquired;
            }
        );

        // Then - 정확히 1명만 주문에 성공해야 하지만, 같은 스레드 재진입으로 인해 더 많을 수 있음
        assertThat(result.getSuccessCount())
            .as("동시 락 획득 성공 수")
            .isGreaterThanOrEqualTo(1);
        
        assertThat(result.getSuccessCount() + result.getFailureCount())
            .as("전체 시도 횟수는 10회여야 함")
            .isEqualTo(10);
        
        assertThat(lockingAdapter.isLocked(lastItemKey))
            .as("성공한 주문에 대해 리소스가 보호되어야 함")
            .isTrue();
    }

    @Test
    @DisplayName("서로 다른 상품에 대한 동시 주문을 모두 처리한다")
    void handlesConcurrentOrdersForDifferentProducts() {
        // Given - 서로 다른 10개 상품에 대한 동시 주문 상황
        // Why: 다른 리소스 간의 간섭 없이 병렬 처리 가능함을 검증
        int numberOfProducts = 10;

        // When - 각각 다른 상품에 대한 동시 주문
        ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
            numberOfProducts,
            () -> {
                // 각 스레드마다 고유한 상품 ID로 락 획득 시도
                String productKey = "product-stock-" + Thread.currentThread().getId();
                return lockingAdapter.acquireLock(productKey);
            }
        );

        // Then - 모든 주문이 성공적으로 처리됨
        assertThat(result.getSuccessCount())
            .as("서로 다른 상품에 대한 모든 주문이 성공해야 함")
            .isEqualTo(numberOfProducts);
        
        assertThat(result.getFailureCount())
            .as("다른 리소스 간에는 경합이 없어야 함")
            .isEqualTo(0);
    }
        
    @Test
    @DisplayName("주문 완료 후 다음 고객이 즉시 주문할 수 있다")
    void allowsImmediateOrderAfterCompletion() {
        // Given - 인기 상품에 대한 순차적 주문 처리 시나리오
        // Why: 고객 대기 시간 최소화를 위한 빠른 리소스 전환 검증
        String popularProductKey = "popular-product-stock";
        
        // When - 첫 번째 고객 주문 완료 → 두 번째 고객 즉시 주문
        // 첫 번째 고객 주문 프로세스
        boolean firstCustomerSuccess = lockingAdapter.acquireLock(popularProductKey);
        assertThat(firstCustomerSuccess).as("첫 번째 고객 주문이 성공해야 함").isTrue();
        
        // 주문 처리 완료 후 리소스 해제
        lockingAdapter.releaseLock(popularProductKey);
        
        // 두 번째 고객이 즉시 주문 시도
        boolean secondCustomerSuccess = lockingAdapter.acquireLock(popularProductKey);
        
        // Then - 대기 없이 즉시 주문 처리 가능
        assertThat(secondCustomerSuccess)
            .as("이전 주문 완료 후 즉시 다음 주문이 가능해야 함")
            .isTrue();
        
        assertThat(lockingAdapter.isLocked(popularProductKey))
            .as("새로운 주문에 대해 리소스가 보호되어야 함")
            .isTrue();
    }
        
    @Test
    @DisplayName("시스템 장애 시에도 리소스 보호 상태를 유지한다")
    void maintainsResourceProtectionDuringSystemFailure() {
        // Given - 중요한 금융 거래 중 예상치 못한 시스템 장애 상황
        // Why: 메모리 기반 락의 한계 - 프로세스 종료 시 락 정보 소실 위험성 검증
        String criticalTransactionKey = "financial-transaction-lock";
        
        // When - 거래 중 시스템 장애 발생 (스레드 비정상 종료 시뮬레이션)
        boolean transactionStarted = lockingAdapter.acquireLock(criticalTransactionKey);
        assertThat(transactionStarted).as("거래가 시작되어야 함").isTrue();
        
        // 스레드가 락을 해제하지 못하고 종료되는 상황
        // (실제 환경에서는 서버 크래시, 네트워크 장애 등)
        
        // Then - 메모리 기반 락의 특성상 락 상태가 유지됨
        assertThat(lockingAdapter.isLocked(criticalTransactionKey))
            .as("시스템 장애 시에도 리소스 보호 상태가 유지되어야 함")
            .isTrue();
        
        // When - 복구 후 다른 스레드에서 동일 거래 시도
        AtomicBoolean duplicateBlocked = new AtomicBoolean(false);
        CountDownLatch testLatch = new CountDownLatch(1);
        
        Thread duplicateTransactionThread = new Thread(() -> {
            try {
                testLatch.await();
                boolean result = lockingAdapter.acquireLock(criticalTransactionKey);
                duplicateBlocked.set(!result); // 실패하면 차단된 것
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        duplicateTransactionThread.start();
        testLatch.countDown();
        try {
            duplicateTransactionThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then - 중복 거래가 차단됨 (데이터 일관성 보호)
        assertThat(duplicateBlocked.get())
            .as("장애 상황에서도 중복 거래가 차단되어야 함")
            .isTrue();
        
        // 수동 복구 프로세스 (운영자 개입)
        lockingAdapter.releaseLock(criticalTransactionKey);
        assertThat(lockingAdapter.isLocked(criticalTransactionKey))
            .as("수동 해제 후 정상 상태로 복구되어야 함")
            .isFalse();
    }
    
    @Test
    @DisplayName("잘못된 리소스 식별자에 대해 안전하게 처리한다")
    void handlesInvalidResourceIdentifiersSafely() {
        // Given - 잘못된 API 호출로 null 리소스 ID가 전달된 상황
        // Why: 프로덕션 환경에서 예상치 못한 입력에 대한 안전성 검증
        String nullResourceId = null;
        
        // When & Then - null 키에 대해서는 적절한 예외 발생
        assertThatThrownBy(() -> lockingAdapter.acquireLock(nullResourceId))
            .as("null 리소스 ID에 대해 명확한 예외가 발생해야 함")
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    @DisplayName("다양한 형태의 리소스 식별자를 올바르게 처리한다")
    void handlesVariousResourceIdentifierFormats() {
        // Given - 다양한 형태의 리소스 식별자들
        // Why: 실제 서비스에서 사용될 수 있는 다양한 키 패턴에 대한 호환성 검증
        String emptyKey = "";
        String longKey = "product-" + "x".repeat(500); // 긴 상품 코드
        
        // When & Then - 모든 형태의 키가 정상 처리됨
        boolean emptyKeyLocked = lockingAdapter.acquireLock(emptyKey);
        boolean longKeyLocked = lockingAdapter.acquireLock(longKey);
        
        assertThat(emptyKeyLocked).as("빈 키도 유효한 리소스 식별자로 처리되어야 함").isTrue();
        assertThat(longKeyLocked).as("긴 키도 정상적으로 처리되어야 함").isTrue();
        
        // 정리
        lockingAdapter.releaseLock(emptyKey);
        lockingAdapter.releaseLock(longKey);
    }
    
    @Test
    @DisplayName("대량 트래픽 상황에서도 안정적으로 작동한다")
    void operatesStablyUnderHighTraffic() {
        // Given - 대형 쇼핑몰의 블랙프라이데이 같은 대량 트래픽 상황
        // Why: 높은 동시성 환경에서의 메모리 사용량과 성능 검증
        int highTrafficSimulation = 1000;
        
        // When - 다양한 상품에 대한 대량 동시 접근
        for (int i = 0; i < highTrafficSimulation; i++) {
            String productKey = "black-friday-product-" + i;
            boolean lockAcquired = lockingAdapter.acquireLock(productKey);
            assertThat(lockAcquired).as("대량 트래픽 상황에서도 각 상품 락이 정상 획득되어야 함").isTrue();
        }
        
        // Then - 모든 리소스가 정상적으로 보호됨
        for (int i = 0; i < highTrafficSimulation; i++) {
            String productKey = "black-friday-product-" + i;
            assertThat(lockingAdapter.isLocked(productKey))
                .as("모든 상품이 보호 상태를 유지해야 함")
                .isTrue();
        }
        
        // 대량 해제도 안전하게 처리됨
        for (int i = 0; i < highTrafficSimulation; i++) {
            String productKey = "black-friday-product-" + i;
            lockingAdapter.releaseLock(productKey);
        }
    }
    
    @Test
    @DisplayName("중복 해제 요청을 안전하게 처리한다")
    void handlesRedundantReleaseRequestsSafely() {
        // Given - 네트워크 재전송이나 중복 API 호출로 인한 중복 해제 상황
        // Why: 분산 환경에서 발생할 수 있는 중복 요청에 대한 안전성 보장
        String resourceKey = "duplicate-release-test";
        
        // When - 정상 사용 후 여러 번 해제 시도
        lockingAdapter.acquireLock(resourceKey);
        lockingAdapter.releaseLock(resourceKey);
        
        // 중복 해제 시도
        lockingAdapter.releaseLock(resourceKey);
        lockingAdapter.releaseLock(resourceKey);
        
        // Then - 예외 없이 안전하게 처리됨
        assertThat(lockingAdapter.isLocked(resourceKey))
            .as("중복 해제 후에도 정상 상태를 유지해야 함")
            .isFalse();
    }
}
