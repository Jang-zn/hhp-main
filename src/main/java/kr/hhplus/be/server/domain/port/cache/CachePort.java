package kr.hhplus.be.server.domain.port.cache;

import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;

public interface CachePort {
    <T> T get(String key, Class<T> type, Supplier<T> supplier);
    
    /**
     * List 타입 전용 캐시 조회 메서드 (unchecked 경고 방지)
     * 
     * @param key 캐시 키
     * @param supplier 캐시 미스 시 데이터를 공급하는 함수
     * @return List 타입의 캐시된 값 또는 새로 생성된 값
     */
    <T> List<T> getList(String key, Supplier<List<T>> supplier);
    
    void put(String key, Object value, int ttlSeconds);
    void evict(String key);
} 