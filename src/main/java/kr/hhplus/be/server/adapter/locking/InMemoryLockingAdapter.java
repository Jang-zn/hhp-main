package kr.hhplus.be.server.adapter.locking;

import kr.hhplus.be.server.domain.port.locking.LockingPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLockingAdapter implements LockingPort {
    
    private final Map<String, Thread> locks = new ConcurrentHashMap<>();
    
    /**
     * Attempts to acquire a lock for the specified key on behalf of the current thread.
     *
     * @param key the identifier for the lock to acquire
     * @return {@code true} if the lock was successfully acquired or is already held by the current thread; {@code false} if another thread holds the lock
     */
    @Override
    public boolean acquireLock(String key) {
        Thread currentThread = Thread.currentThread();
        Thread existingThread = locks.putIfAbsent(key, currentThread);
        return existingThread == null || existingThread == currentThread;
    }
    
    /**
     * Releases the lock associated with the specified key, allowing other threads to acquire it.
     *
     * @param key the identifier for the lock to be released
     */
    @Override
    public void releaseLock(String key) {
        locks.remove(key);
    }
    
    /**
     * Checks whether the specified key is currently locked.
     *
     * @param key the lock key to check
     * @return {@code true} if the key is locked; {@code false} otherwise
     */
    @Override
    public boolean isLocked(String key) {
        return locks.containsKey(key);
    }
} 