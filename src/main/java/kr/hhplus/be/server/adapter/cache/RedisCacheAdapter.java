package kr.hhplus.be.server.adapter.cache;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis(Redisson)를 이용한 캐시 구현체
 * 
 * 분산 환경에서 캐시를 공유하기 위한 Redis 기반 캐시 어댑터입니다.
 * Redisson의 RBucket을 사용하여 JSON 직렬화/역직렬화를 지원합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {
    
    private final RedissonClient redissonClient;
    
    // 캐시 키 접두사
    private static final String CACHE_KEY_PREFIX = "cache:";
    
    /**
     * 캐시에서 값을 조회하고, 없으면 supplier로부터 값을 가져와 캐시에 저장
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @param supplier 캐시 미스 시 값을 공급하는 함수
     * @return 캐시된 값 또는 새로 생성된 값
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, Supplier<T> supplier) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<T> bucket = redissonClient.getBucket(cacheKey);
            T cachedValue = bucket.get();
            
            if (cachedValue != null) {
                log.debug("Cache hit: key={}, type={}", cacheKey, type.getSimpleName());
                return cachedValue;
            }
            
            // 캐시 미스 - supplier에서 값을 가져와서 캐시에 저장
            log.debug("Cache miss: key={}, type={}", cacheKey, type.getSimpleName());
            T suppliedValue = supplier.get();
            
            if (suppliedValue != null) {
                bucket.set(suppliedValue);
                log.debug("Cache stored: key={}, type={}", cacheKey, type.getSimpleName());
            }
            
            return suppliedValue;
            
        } catch (Exception e) {
            log.error("Error accessing cache: key={}, type={}", cacheKey, type.getSimpleName(), e);
            // 캐시 오류 시 supplier로부터 직접 값 반환
            return supplier.get();
        }
    }
    
    /**
     * 캐시에 값을 저장 (TTL 설정)
     * 
     * @param key 캐시 키
     * @param value 저장할 값
     * @param ttlSeconds TTL (초 단위)
     */
    @Override
    public void put(String key, Object value, int ttlSeconds) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
            
            if (ttlSeconds > 0) {
                bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
                log.debug("Cache put with TTL: key={}, ttl={}s", cacheKey, ttlSeconds);
            } else {
                bucket.set(value);
                log.debug("Cache put without TTL: key={}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("Error putting cache: key={}, ttl={}s", cacheKey, ttlSeconds, e);
        }
    }
    
    /**
     * 캐시에서 값을 삭제
     * 
     * @param key 캐시 키
     */
    @Override
    public void evict(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
            boolean deleted = bucket.delete();
            
            if (deleted) {
                log.debug("Cache evicted: key={}", cacheKey);
            } else {
                log.debug("Cache eviction failed (key not found): key={}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("Error evicting cache: key={}", cacheKey, e);
        }
    }
    
    /**
     * TTL이 있는 캐시 조회 및 저장
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @param supplier 캐시 미스 시 값을 공급하는 함수
     * @param ttlSeconds TTL (초 단위)
     * @return 캐시된 값 또는 새로 생성된 값
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithTTL(String key, Class<T> type, Supplier<T> supplier, int ttlSeconds) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<T> bucket = redissonClient.getBucket(cacheKey);
            T cachedValue = bucket.get();
            
            if (cachedValue != null) {
                log.debug("Cache hit with TTL: key={}, type={}", cacheKey, type.getSimpleName());
                return cachedValue;
            }
            
            // 캐시 미스 - supplier에서 값을 가져와서 TTL과 함께 캐시에 저장
            log.debug("Cache miss with TTL: key={}, type={}, ttl={}s", cacheKey, type.getSimpleName(), ttlSeconds);
            T suppliedValue = supplier.get();
            
            if (suppliedValue != null) {
                if (ttlSeconds > 0) {
                    bucket.set(suppliedValue, ttlSeconds, TimeUnit.SECONDS);
                } else {
                    bucket.set(suppliedValue);
                }
                log.debug("Cache stored with TTL: key={}, type={}, ttl={}s", cacheKey, type.getSimpleName(), ttlSeconds);
            }
            
            return suppliedValue;
            
        } catch (Exception e) {
            log.error("Error accessing cache with TTL: key={}, type={}, ttl={}s", cacheKey, type.getSimpleName(), ttlSeconds, e);
            return supplier.get();
        }
    }
    
    /**
     * 캐시 키 존재 여부 확인
     * 
     * @param key 캐시 키
     * @return 존재 여부
     */
    public boolean exists(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
            boolean exists = bucket.isExists();
            log.debug("Cache exists check: key={}, exists={}", cacheKey, exists);
            return exists;
            
        } catch (Exception e) {
            log.error("Error checking cache existence: key={}", cacheKey, e);
            return false;
        }
    }
    
    /**
     * 캐시 TTL 확인 (밀리초 단위)
     * 
     * @param key 캐시 키
     * @return TTL (밀리초), -1: 만료 시간 없음, -2: 키 없음
     */
    public long getTTL(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
            long ttl = bucket.remainTimeToLive();
            log.debug("Cache TTL check: key={}, ttl={}ms", cacheKey, ttl);
            return ttl;
            
        } catch (Exception e) {
            log.error("Error getting cache TTL: key={}", cacheKey, e);
            return -2; // 에러 시 키 없음으로 처리
        }
    }
}