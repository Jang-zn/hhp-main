package kr.hhplus.be.server.domain.port.cache;

import java.util.Optional;

import java.util.function.Supplier;

public interface CachePort {
    <T> T get(String key, Class<T> type, Supplier<T> supplier);
    void put(String key, Object value, int ttlSeconds);
    void evict(String key);
} 