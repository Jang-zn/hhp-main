package kr.hhplus.be.server.adapter.cache;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class InMemoryCacheAdapter implements CachePort {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public <T> T get(String key, Class<T> type, Supplier<T> supplier) {
        Object value = cache.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        T suppliedValue = supplier.get();
        if (suppliedValue != null) {
            cache.put(key, suppliedValue);
        }
        return suppliedValue;
    }

    @Override
    public void put(String key, Object value, int ttlSeconds) {
        cache.put(key, value);
    }

    @Override
    public void evict(String key) {
        cache.remove(key);
    }
} 