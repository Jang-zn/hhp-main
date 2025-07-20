package kr.hhplus.be.server.unit.adapter.locking;

import kr.hhplus.be.server.adapter.locking.InMemoryLockingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryLockingAdapter 단위 테스트")
class InMemoryLockingAdapterTest {

    private InMemoryLockingAdapter lockingAdapter;

    @BeforeEach
    void setUp() {
        lockingAdapter = new InMemoryLockingAdapter();
    }

    @Nested
    @DisplayName("락 획득 및 해제 테스트")
    class LockAcquisitionAndReleaseTests {

        @Test
        @DisplayName("성공케이스: 단일 스레드가 락을 획득하고 해제한다")
        void acquireAndReleaseLock_Success() {
            // given
            String key = "testKey";

            // when
            boolean acquired = lockingAdapter.acquireLock(key);

            // then
            assertThat(acquired).isTrue();
            assertThat(lockingAdapter.isLocked(key)).isTrue();

            // when
            lockingAdapter.releaseLock(key);

            // then
            assertThat(lockingAdapter.isLocked(key)).isFalse();
        }

        @Test
        @DisplayName("성공케이스: 동일 스레드가 락을 재획득한다 (Re-entrant)")
        void acquireLock_Reentrant_Success() {
            // given
            String key = "reentrantKey";

            // when
            boolean firstAcquired = lockingAdapter.acquireLock(key);
            boolean secondAcquired = lockingAdapter.acquireLock(key);

            // then
            assertThat(firstAcquired).isTrue();
            assertThat(secondAcquired).isTrue();
            assertThat(lockingAdapter.isLocked(key)).isTrue();

            // when
            lockingAdapter.releaseLock(key);

            // then
            assertThat(lockingAdapter.isLocked(key)).isFalse();
        }

        @Test
        @DisplayName("실패케이스: 다른 스레드가 점유 중인 락을 획득하려고 시도하면 실패한다")
        void acquireLock_WhenAlreadyLockedByOtherThread_Fails() throws InterruptedException {
            // given
            String key = "contendedKey";
            CountDownLatch latch = new CountDownLatch(1);

            // 첫 번째 스레드가 락을 획득
            Thread firstThread = new Thread(() -> {
                lockingAdapter.acquireLock(key);
                latch.countDown();
            });
            firstThread.start();
            latch.await(5, TimeUnit.SECONDS);

            // when
            // 두 번째 스레드가 동일한 락 획득 시도
            boolean acquiredBySecondThread = lockingAdapter.acquireLock(key);

            // then
            assertThat(acquiredBySecondThread).isFalse();
            assertThat(lockingAdapter.isLocked(key)).isTrue();

            // cleanup
            firstThread.interrupt();
        }

        @Test
        @DisplayName("성공케이스: 락이 해제된 후 다른 스레드가 락을 획득할 수 있다")
        void acquireLock_AfterReleaseByOtherThread_Success() {
            // given
            String key = "releasedKey";

            // 첫 번째 스레드가 락을 획득했다가 해제
            lockingAdapter.acquireLock(key);
            lockingAdapter.releaseLock(key);

            // when
            // 두 번째 스레드가 동일한 락 획득 시도
            boolean acquiredBySecondThread = lockingAdapter.acquireLock(key);

            // then
            assertThat(acquiredBySecondThread).isTrue();
            assertThat(lockingAdapter.isLocked(key)).isTrue();
        }
    }

    @Nested
    @DisplayName("락 상태 확인 테스트")
    class IsLockedTests {

        @Test
        @DisplayName("성공케이스: 락이 걸려있을 때 isLocked는 true를 반환한다")
        void isLocked_WhenLocked_ReturnsTrue() {
            // given
            String key = "lockedKey";
            lockingAdapter.acquireLock(key);

            // when
            boolean isLocked = lockingAdapter.isLocked(key);

            // then
            assertThat(isLocked).isTrue();
        }

        @Test
        @DisplayName("성공케이스: 락이 해제되었을 때 isLocked는 false를 반환한다")
        void isLocked_WhenUnlocked_ReturnsFalse() {
            // given
            String key = "unlockedKey";
            lockingAdapter.acquireLock(key);
            lockingAdapter.releaseLock(key);

            // when
            boolean isLocked = lockingAdapter.isLocked(key);

            // then
            assertThat(isLocked).isFalse();
        }

        @Test
        @DisplayName("성공케이스: 존재하지 않는 키에 대해 isLocked는 false를 반환한다")
        void isLocked_ForNonExistentKey_ReturnsFalse() {
            // given
            String key = "nonExistentKey";

            // when
            boolean isLocked = lockingAdapter.isLocked(key);

            // then
            assertThat(isLocked).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 여러 스레드가 동일한 락을 동시에 획득 시도 시 하나만 성공한다")
        void acquireLock_ConcurrentAccess_OnlyOneSucceeds() throws InterruptedException {
            // given
            String key = "concurrentKey";
            int numberOfThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < numberOfThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (lockingAdapter.acquireLock(key)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 모든 스레드 동시 시작
            doneLatch.await(10, TimeUnit.SECONDS); // 모든 스레드 완료 대기

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(lockingAdapter.isLocked(key)).isTrue();

            // cleanup
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 여러 스레드가 다른 락을 동시에 획득 시도 시 모두 성공한다")
        void acquireLock_ConcurrentAccessOnDifferentKeys_AllSucceed() throws InterruptedException {
            // given
            int numberOfThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < numberOfThreads; i++) {
                final String key = "concurrentKey_" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (lockingAdapter.acquireLock(key)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(numberOfThreads);
            for (int i = 0; i < numberOfThreads; i++) {
                assertThat(lockingAdapter.isLocked("concurrentKey_" + i)).isTrue();
            }

            // cleanup
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
        
        @Test
        @DisplayName("동시성 테스트: 락 해제 후 다른 스레드가 즉시 획득할 수 있다")
        void acquireLock_AfterConcurrentRelease_Success() throws InterruptedException {
            // given
            String key = "releaseKey";
            int numberOfRounds = 5;
            ExecutorService executor = Executors.newFixedThreadPool(2);
            AtomicInteger totalSuccessCount = new AtomicInteger(0);
            
            // when
            for (int round = 0; round < numberOfRounds; round++) {
                CountDownLatch roundStartLatch = new CountDownLatch(1);
                CountDownLatch roundDoneLatch = new CountDownLatch(2);
                AtomicInteger roundSuccessCount = new AtomicInteger(0);
                
                // 첫 번째 스레드: 락 획득 → 해제
                executor.submit(() -> {
                    try {
                        roundStartLatch.await();
                        if (lockingAdapter.acquireLock(key)) {
                            roundSuccessCount.incrementAndGet();
                            Thread.sleep(10); // 잠시 보유
                            lockingAdapter.releaseLock(key);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        roundDoneLatch.countDown();
                    }
                });
                
                // 두 번째 스레드: 락 획득 시도
                executor.submit(() -> {
                    try {
                        roundStartLatch.await();
                        Thread.sleep(20); // 첫 번째 스레드가 해제할 때까지 대기
                        if (lockingAdapter.acquireLock(key)) {
                            roundSuccessCount.incrementAndGet();
                            lockingAdapter.releaseLock(key);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        roundDoneLatch.countDown();
                    }
                });
                
                roundStartLatch.countDown();
                roundDoneLatch.await(5, TimeUnit.SECONDS);
                
                totalSuccessCount.addAndGet(roundSuccessCount.get());
            }
            
            // then
            assertThat(totalSuccessCount.get()).isEqualTo(numberOfRounds * 2); // 각 라운드마다 2번의 성공
            assertThat(lockingAdapter.isLocked(key)).isFalse(); // 마지막에는 해제됨
            
            // cleanup
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
        
        @Test
        @DisplayName("동시성 테스트: 스레드 종료 시에도 락이 유지된다 (메모리 누수 시나리오)")
        void acquireLock_ThreadTermination_LockPersists() throws InterruptedException {
            // given
            String key = "threadTerminationKey";
            CountDownLatch threadStartLatch = new CountDownLatch(1);
            CountDownLatch threadAcquiredLatch = new CountDownLatch(1);
            
            // when
            Thread lockingThread = new Thread(() -> {
                try {
                    threadStartLatch.await();
                    if (lockingAdapter.acquireLock(key)) {
                        threadAcquiredLatch.countDown();
                        // 스레드가 락을 해제하지 않고 종료됨
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            lockingThread.start();
            threadStartLatch.countDown();
            threadAcquiredLatch.await(5, TimeUnit.SECONDS);
            
            // 스레드 종료까지 대기
            lockingThread.join(5000);
            
            // then
            assertThat(lockingAdapter.isLocked(key)).isTrue(); // 스레드가 종료되어도 락은 유지됨
            
            // 다른 스레드가 같은 락 획득 시도
            boolean acquiredByOtherThread = lockingAdapter.acquireLock(key);
            assertThat(acquiredByOtherThread).isFalse(); // 여전히 락이 걸려있어서 실패
            
            // 명시적으로 해제해야 함
            lockingAdapter.releaseLock(key);
            assertThat(lockingAdapter.isLocked(key)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("에지 케이스 테스트")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("에지케이스: null 키에 대한 락 획득 시도")
        void acquireLock_WithNullKey_HandlesGracefully() {
            // given
            String key = null;
            
            // when & then
            // ConcurrentHashMap은 null 키를 허용하지 않으므로 예외가 발생해야 함
            try {
                lockingAdapter.acquireLock(key);
                // 예외가 발생하지 않으면 테스트 실패
                assertThat(true).describedAs("null 키에 대해 예외가 발생해야 함").isFalse();
            } catch (NullPointerException e) {
                // 예상된 동작
                assertThat(e).isInstanceOf(NullPointerException.class);
            }
        }
        
        @Test
        @DisplayName("에지케이스: 빈 문자열 키에 대한 락 획득")
        void acquireLock_WithEmptyKey_Success() {
            // given
            String key = "";
            
            // when
            boolean acquired = lockingAdapter.acquireLock(key);
            
            // then
            assertThat(acquired).isTrue();
            assertThat(lockingAdapter.isLocked(key)).isTrue();
            
            // cleanup
            lockingAdapter.releaseLock(key);
        }
        
        @Test
        @DisplayName("에지케이스: 매우 긴 키에 대한 락 획득")
        void acquireLock_WithVeryLongKey_Success() {
            // given
            String key = "a".repeat(1000); // 1000자 키
            
            // when
            boolean acquired = lockingAdapter.acquireLock(key);
            
            // then
            assertThat(acquired).isTrue();
            assertThat(lockingAdapter.isLocked(key)).isTrue();
            
            // cleanup
            lockingAdapter.releaseLock(key);
        }
        
        @Test
        @DisplayName("에지케이스: 존재하지 않는 락을 해제하려고 시도")
        void releaseLock_NonExistentKey_NoException() {
            // given
            String key = "nonExistentKey";
            
            // when & then
            // 예외가 발생하지 않아야 함
            lockingAdapter.releaseLock(key);
            assertThat(lockingAdapter.isLocked(key)).isFalse();
        }
        
        @Test
        @DisplayName("에지케이스: 다중 해제 시도")
        void releaseLock_MultipleReleases_NoException() {
            // given
            String key = "multiReleaseKey";
            lockingAdapter.acquireLock(key);
            
            // when
            lockingAdapter.releaseLock(key);
            lockingAdapter.releaseLock(key); // 두 번째 해제
            lockingAdapter.releaseLock(key); // 세 번째 해제
            
            // then
            assertThat(lockingAdapter.isLocked(key)).isFalse();
        }
        
        @Test
        @DisplayName("에지케이스: 대량의 서로 다른 락 처리")
        void acquireLock_ManyDifferentKeys_Success() {
            // given
            int numberOfKeys = 1000;
            
            // when
            for (int i = 0; i < numberOfKeys; i++) {
                String key = "key_" + i;
                boolean acquired = lockingAdapter.acquireLock(key);
                assertThat(acquired).isTrue();
            }
            
            // then
            for (int i = 0; i < numberOfKeys; i++) {
                String key = "key_" + i;
                assertThat(lockingAdapter.isLocked(key)).isTrue();
            }
            
            // cleanup
            for (int i = 0; i < numberOfKeys; i++) {
                String key = "key_" + i;
                lockingAdapter.releaseLock(key);
                assertThat(lockingAdapter.isLocked(key)).isFalse();
            }
        }
    }
}
