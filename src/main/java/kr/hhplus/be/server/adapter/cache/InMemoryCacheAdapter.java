package kr.hhplus.be.server.adapter.cache;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCacheAdapter implements CachePort {
    
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = cache.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }
    
    @Override
    public void set(String key, Object value) {
        cache.put(key, value);
    }
    
    @Override
    public void delete(String key) {
        cache.remove(key);
    }
    
    @Override
    public boolean exists(String key) {
        return cache.containsKey(key);
    }
} 