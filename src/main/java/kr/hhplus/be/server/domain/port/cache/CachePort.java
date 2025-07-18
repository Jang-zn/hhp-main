package kr.hhplus.be.server.domain.port.cache;

import java.util.Optional;

public interface CachePort {
    <T> Optional<T> get(String key, Class<T> type);
    void set(String key, Object value);
    void delete(String key);
    boolean exists(String key);
} 