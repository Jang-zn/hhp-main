package kr.hhplus.be.server.adapter.locking;

import kr.hhplus.be.server.domain.port.locking.LockingPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLockingAdapter implements LockingPort {
    
    private final Map<String, Thread> locks = new ConcurrentHashMap<>();
    
    /**
     * 테스트 전용 메서드: 모든 락을 클리어합니다.
     * 프로덕션 코드에서 사용하지 마세요.
     * 
     * @deprecated 이 메서드는 테스트에서만 사용되어야 합니다.
     */
    @Deprecated(forRemoval = false)
    public void clearAllLocks() {
        locks.clear();
    }
    
    @Override
    public boolean acquireLock(String key) {
        Thread currentThread = Thread.currentThread();
        Thread existingThread = locks.putIfAbsent(key, currentThread);
        return existingThread == null || existingThread == currentThread;
    }
    
    @Override
    public void releaseLock(String key) {
        locks.remove(key);
    }
    
    @Override
    public boolean isLocked(String key) {
        return locks.containsKey(key);
    }
} 