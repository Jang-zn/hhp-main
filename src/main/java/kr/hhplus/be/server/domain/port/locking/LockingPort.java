package kr.hhplus.be.server.domain.port.locking;

public interface LockingPort {
    boolean acquireLock(String key);
    void releaseLock(String key);
    boolean isLocked(String key);
} 