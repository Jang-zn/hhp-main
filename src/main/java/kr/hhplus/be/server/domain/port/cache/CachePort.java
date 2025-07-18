package kr.hhplus.be.server.domain.port.cache;

import java.util.Optional;

public interface CachePort {
    /**
 * Retrieves a cached value by key and attempts to return it as an instance of the specified type.
 *
 * @param key the cache key to look up
 * @param type the class of the expected return type
 * @return an {@code Optional} containing the value if present and of the correct type, or an empty {@code Optional} if not found or type mismatch
 */
<T> Optional<T> get(String key, Class<T> type);
    /**
 * Stores a value in the cache associated with the specified key.
 *
 * @param key the key under which the value will be stored
 * @param value the value to cache
 */
void set(String key, Object value);
    /**
 * Removes the cache entry associated with the specified key.
 *
 * If the key does not exist, this operation has no effect.
 *
 * @param key the key identifying the cache entry to remove
 */
void delete(String key);
    /**
 * Checks whether a cache entry exists for the specified key.
 *
 * @param key the key to check in the cache
 * @return {@code true} if a cache entry exists for the key; {@code false} otherwise
 */
boolean exists(String key);
} 