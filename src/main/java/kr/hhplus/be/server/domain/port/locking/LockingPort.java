package kr.hhplus.be.server.domain.port.locking;

public interface LockingPort {
    /**
 * Attempts to acquire a lock identified by the specified key.
 *
 * @param key the unique identifier for the lock
 * @return true if the lock was successfully acquired; false if the lock is already held
 */
boolean acquireLock(String key);
    /**
 * Releases the lock associated with the specified key.
 *
 * @param key the identifier of the lock to release
 */
void releaseLock(String key);
    /**
 * Checks whether the lock identified by the specified key is currently held.
 *
 * @param key the identifier for the lock to check
 * @return true if the lock is held; false otherwise
 */
boolean isLocked(String key);
} 