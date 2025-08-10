package kr.hhplus.be.server.adapter.locking;

import kr.hhplus.be.server.domain.port.locking.LockingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis(Redisson)를 이용한 분산 락 구현체
 * 
 * 분산 환경에서 동시성 제어를 위한 락 메커니즘을 제공한다.
 * Redisson의 RLock을 사용하여 공정성과 재진입을 지원한다.
 */
@Slf4j
@Component
@Profile({"!test", "integration-test"}) // 테스트 환경에서는 InMemoryLockingAdapter 사용, 통합 테스트에서는 Redis 사용
@RequiredArgsConstructor
public class RedisLockingAdapter implements LockingPort {
    
    private final RedissonClient redissonClient;
    
    // 락 설정값
    private static final long DEFAULT_WAIT_TIME = 5L; // 5초 대기
    private static final long DEFAULT_LEASE_TIME = 10L; // 10초 보유
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    private static final String LOCK_KEY_PREFIX = "lock:";
    
    /**
     * 분산 락 획득
     * 
     * @param key 락 키
     * @return 락 획득 성공 여부
     */
    @Override
    public boolean acquireLock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey); // 공정한 락 사용 (FIFO)
        
        try {
            boolean acquired = lock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TIME_UNIT);
            
            if (acquired) {
                log.debug("Lock acquired successfully: key={}, thread={}", 
                    lockKey, Thread.currentThread().getName());
            } else {
                log.debug("Failed to acquire lock: key={}, thread={}", 
                    lockKey, Thread.currentThread().getName());
            }
            
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock: key={}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.error("Error acquiring lock: key={}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 분산 락 해제
     * 
     * @param key 락 키
     */
    @Override
    public void releaseLock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released successfully: key={}, thread={}", 
                    lockKey, Thread.currentThread().getName());
            } else {
                log.warn("Attempted to release lock not held by current thread: key={}, thread={}", 
                    lockKey, Thread.currentThread().getName());
            }
        } catch (Exception e) {
            log.error("Error releasing lock: key={}", lockKey, e);
        }
    }
    
    /**
     * 락 상태 확인
     * 
     * @param key 락 키
     * @return 락이 걸려있는지 여부
     */
    @Override
    public boolean isLocked(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            boolean locked = lock.isLocked();
            log.debug("Lock status checked: key={}, locked={}", lockKey, locked);
            return locked;
        } catch (Exception e) {
            log.error("Error checking lock status: key={}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 커스텀 설정으로 락 획득 (확장 메서드)
     * 
     * @param key 락 키
     * @param waitTime 대기 시간
     * @param leaseTime 락 보유 시간
     * @param timeUnit 시간 단위
     * @return 락 획득 성공 여부
     */
    public boolean acquireLockWithCustomSettings(String key, long waitTime, long leaseTime, TimeUnit timeUnit) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock with custom settings: key={}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 현재 스레드가 락을 보유하고 있는지 확인
     * 
     * @param key 락 키
     * @return 현재 스레드의 락 보유 여부
     */
    public boolean isHeldByCurrentThread(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);
        return lock.isHeldByCurrentThread();
    }
    
    /**
     * 락 강제 해제 (관리자 기능)
     * 주의: 일반적인 비즈니스 로직에서 사용하지 말 것
     * 
     * @param key 락 키
     */
    public void forceUnlock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            lock.forceUnlock();
            log.warn("Lock forcefully released: key={}", lockKey);
        } catch (Exception e) {
            log.error("Error forcefully releasing lock: key={}", lockKey, e);
        }
    }
}