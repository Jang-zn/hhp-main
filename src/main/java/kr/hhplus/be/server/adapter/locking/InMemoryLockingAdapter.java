package kr.hhplus.be.server.adapter.locking;

import kr.hhplus.be.server.domain.port.locking.LockingPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLockingAdapter implements LockingPort {
    
    private final Map<String, Thread> locks = new ConcurrentHashMap<>();
    
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