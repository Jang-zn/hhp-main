package kr.hhplus.be.server.adapter.cache;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCacheAdapter implements CachePort {
    
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    /**
     * Retrieves a cached value by key if it exists and matches the specified type.
     *
     * @param key  the cache key to look up
     * @param type the expected class type of the cached value
     * @return an {@code Optional} containing the value if present and of the correct type; otherwise, an empty {@code Optional}
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = cache.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }
    
    /**
     * Stores or updates the value associated with the specified key in the cache.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be cached
     */
    @Override
    public void set(String key, Object value) {
        cache.put(key, value);
    }
    
    /**
     * Removes the cache entry associated with the specified key.
     *
     * If the key does not exist, this operation has no effect.
     *
     * @param key the key whose cache entry should be removed
     */
    @Override
    public void delete(String key) {
        cache.remove(key);
    }
    
    /**
     * Checks whether a cache entry exists for the specified key.
     *
     * @param key the key to check for existence in the cache
     * @return true if the cache contains an entry for the key; false otherwise
     */
    @Override
    public boolean exists(String key) {
        return cache.containsKey(key);
    }
} 