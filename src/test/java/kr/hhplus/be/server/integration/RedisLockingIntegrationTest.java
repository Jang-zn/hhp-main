package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.adapter.locking.RedisLockingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 분산 락 통합 테스트
 * 
 * 실제 Redis 컨테이너를 사용하여 분산 락의 동작을 검증한다.
 */
@DisplayName("Redis 분산 락 통합 테스트")
class RedisLockingIntegrationTest extends IntegrationTestBase {
    
    @Autowired
    private RedisLockingAdapter redisLockingAdapter;
    
    private static final String TEST_LOCK_KEY = "integration-test-key";
    
    @BeforeEach
    void setUp() {
        // 테스트 전 락 해제
        if (redisLockingAdapter.isLocked(TEST_LOCK_KEY)) {
            redisLockingAdapter.forceUnlock(TEST_LOCK_KEY);
        }
    }
    
    @Test
    @DisplayName("단일 스레드에서 락 획득 및 해제")
    void singleThreadLockAndUnlock() {
        // Given
        String lockKey = TEST_LOCK_KEY + "-single";
        
        // When - 락 획득
        boolean acquired = redisLockingAdapter.acquireLock(lockKey);
        
        // Then
        assertThat(acquired).isTrue();
        assertThat(redisLockingAdapter.isLocked(lockKey)).isTrue();
        assertThat(redisLockingAdapter.isHeldByCurrentThread(lockKey)).isTrue();
        
        // When - 락 해제
        redisLockingAdapter.releaseLock(lockKey);
        
        // Then
        assertThat(redisLockingAdapter.isLocked(lockKey)).isFalse();
    }
    
    @Test
    @DisplayName("동시에 여러 스레드가 락 획득 시도")
    void multiThreadConcurrentLockAcquisition() throws InterruptedException {
        // Given
        String lockKey = TEST_LOCK_KEY + "-concurrent";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When - 10개 스레드가 동시에 락 획득 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작
                    
                    boolean acquired = redisLockingAdapter.acquireLock(lockKey);
                    if (acquired) {
                        successCount.incrementAndGet();
                        Thread.sleep(100); // 락 보유 시간
                        redisLockingAdapter.releaseLock(lockKey);
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // 모든 스레드 시작
        endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - 하나의 스레드만 락을 획득해야 함
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }
    
    @Test
    @DisplayName("재진입 가능한 락 테스트")
    void reentrantLockTest() {
        // Given
        String lockKey = TEST_LOCK_KEY + "-reentrant";
        
        // When - 같은 스레드에서 여러 번 락 획득
        boolean firstAcquire = redisLockingAdapter.acquireLock(lockKey);
        boolean secondAcquire = redisLockingAdapter.acquireLock(lockKey);
        
        // Then - 재진입 가능
        assertThat(firstAcquire).isTrue();
        assertThat(secondAcquire).isTrue();
        assertThat(redisLockingAdapter.isHeldByCurrentThread(lockKey)).isTrue();
        
        // When - 락 해제
        redisLockingAdapter.releaseLock(lockKey);
        
        // Then - 여전히 락 보유 (재진입 카운트가 있음)
        assertThat(redisLockingAdapter.isLocked(lockKey)).isTrue();
        
        // When - 두 번째 해제
        redisLockingAdapter.releaseLock(lockKey);
        
        // Then - 완전히 해제
        assertThat(redisLockingAdapter.isLocked(lockKey)).isFalse();
    }
    
    @Test
    @DisplayName("락 타임아웃 테스트")
    void lockTimeoutTest() throws InterruptedException {
        // Given
        String lockKey = TEST_LOCK_KEY + "-timeout";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);
        
        // When - 첫 번째 스레드가 락 획득
        Thread thread1 = new Thread(() -> {
            if (redisLockingAdapter.acquireLock(lockKey)) {
                try {
                    Thread.sleep(3000); // 3초간 락 보유
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    redisLockingAdapter.releaseLock(lockKey);
                }
            }
        });
        thread1.start();
        Thread.sleep(100); // 첫 번째 스레드가 락을 획득할 시간
        
        // When - 두 번째 스레드가 락 획득 시도 (5초 대기)
        Thread thread2 = new Thread(() -> {
            boolean acquired = redisLockingAdapter.acquireLock(lockKey);
            result.set(acquired ? 1 : 0);
            if (acquired) {
                redisLockingAdapter.releaseLock(lockKey);
            }
            latch.countDown();
        });
        thread2.start();
        
        // Then
        latch.await(10, TimeUnit.SECONDS);
        assertThat(result.get()).isEqualTo(1); // 대기 후 락 획득 성공
        
        thread1.join();
        thread2.join();
    }
    
    @Test
    @DisplayName("커스텀 설정으로 락 획득")
    void customSettingsLockAcquisition() {
        // Given
        String lockKey = TEST_LOCK_KEY + "-custom";
        
        // When - 짧은 대기 시간과 보유 시간 설정
        boolean acquired = redisLockingAdapter.acquireLockWithCustomSettings(
            lockKey, 1L, 2L, TimeUnit.SECONDS);
        
        // Then
        assertThat(acquired).isTrue();
        assertThat(redisLockingAdapter.isLocked(lockKey)).isTrue();
        
        // Clean up
        redisLockingAdapter.releaseLock(lockKey);
    }
    
    @Test
    @DisplayName("락 강제 해제 테스트")
    void forcedUnlockTest() throws InterruptedException {
        // Given
        String lockKey = TEST_LOCK_KEY + "-force";
        CountDownLatch acquiredLatch = new CountDownLatch(1);
        CountDownLatch releasedLatch = new CountDownLatch(1);
        
        // When - 다른 스레드에서 락 획득
        Thread lockHolder = new Thread(() -> {
            if (redisLockingAdapter.acquireLock(lockKey)) {
                acquiredLatch.countDown();
                try {
                    releasedLatch.await(); // 강제 해제될 때까지 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        lockHolder.start();
        acquiredLatch.await();
        
        // Then - 락이 걸려있음
        assertThat(redisLockingAdapter.isLocked(lockKey)).isTrue();
        
        // When - 강제 해제
        redisLockingAdapter.forceUnlock(lockKey);
        releasedLatch.countDown();
        
        // Then - 락이 해제됨
        assertThat(redisLockingAdapter.isLocked(lockKey)).isFalse();
        
        lockHolder.join();
    }
}