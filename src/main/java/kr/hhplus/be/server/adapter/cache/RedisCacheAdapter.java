package kr.hhplus.be.server.adapter.cache;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.List;
import java.util.Random;

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
    private final Random random = new Random();
    
    private static final String CACHE_KEY_PREFIX = "cache:";
    
    /**
     * 캐시에서 값을 조회 (Cache Stampede 방어 포함)
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @return 캐시된 값 또는 null
     */
    @Override
    public <T> T get(String key, Class<T> type) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            // 1. 첫 번째 캐시 확인
            RBucket<T> bucket = redissonClient.getBucket(cacheKey);
            T cachedValue = bucket.get();
            
            if (cachedValue != null) {
                log.debug("Cache hit: key={}, type={}", cacheKey, type.getSimpleName());
                return cachedValue;
            }
            
            // 2. Cache Miss - Cache Stampede 방어
            String lockKey = cacheKey + ":load";
            RLock lock = redissonClient.getLock(lockKey);
            
            try {
                // 3. Redisson pub/sub 대기 메커니즘 활용 (200ms 대기)
                if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    try {
                        // 4. Double-check (다른 스레드가 이미 로드했을 수 있음)
                        cachedValue = bucket.get();
                        if (cachedValue != null) {
                            log.debug("Cache hit after lock (double-check): key={}, type={}", cacheKey, type.getSimpleName());
                            return cachedValue;
                        }
                        
                        // 5. 여전히 없으면 null 반환 (서비스가 DB 조회 후 put() 호출하도록)
                        log.debug("Cache miss after lock: key={}, type={}", cacheKey, type.getSimpleName());
                        return null;
                        
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // 6. 200ms 대기했는데도 락 획득 실패 - DB 폴백
                    log.debug("Lock acquisition timeout, fallback to DB: key={}", cacheKey);
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted during lock wait: key={}", cacheKey);
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error accessing cache: key={}, type={}", cacheKey, type.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 캐시에 값을 저장 (TTL 설정 + Cache Stampede 방지용 랜덤화)
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
                // Cache Stampede 방지: TTL에 ±10% 랜덤 지터 추가
                int jitter = (int) (ttlSeconds * 0.1 * (random.nextDouble() * 2 - 1)); // -10% ~ +10%
                int randomizedTTL = ttlSeconds + jitter;
                
                bucket.set(value, randomizedTTL, TimeUnit.SECONDS);
                log.debug("Cache put with randomized TTL: key={}, originalTTL={}s, actualTTL={}s", 
                         cacheKey, ttlSeconds, randomizedTTL);
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
     * List 타입 캐시 조회 (Cache Stampede 방어 포함)
     * 
     * @param key 캐시 키
     * @return 캐시된 List 또는 null
     */
    @Override
    public <T> List<T> getList(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        
        try {
            // 1. 첫 번째 캐시 확인
            RBucket<List<T>> bucket = redissonClient.getBucket(cacheKey);
            List<T> cachedValue = bucket.get();
            
            if (cachedValue != null) {
                log.debug("Cache hit (List): key={}, size={}", cacheKey, cachedValue.size());
                return cachedValue;
            }
            
            // 2. Cache Miss - Cache Stampede 방어
            String lockKey = cacheKey + ":load";
            RLock lock = redissonClient.getLock(lockKey);
            
            try {
                // 3. Redisson pub/sub 대기 메커니즘 활용 (200ms 대기)
                if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    try {
                        // 4. Double-check (다른 스레드가 이미 로드했을 수 있음)
                        cachedValue = bucket.get();
                        if (cachedValue != null) {
                            log.debug("Cache hit (List) after lock (double-check): key={}, size={}", cacheKey, cachedValue.size());
                            return cachedValue;
                        }
                        
                        // 5. 여전히 없으면 null 반환 (서비스가 DB 조회 후 put() 호출하도록)
                        log.debug("Cache miss (List) after lock: key={}", cacheKey);
                        return null;
                        
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // 6. 200ms 대기했는데도 락 획득 실패 - DB 폴백
                    log.debug("Lock acquisition timeout, fallback to DB (List): key={}", cacheKey);
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted during lock wait (List): key={}", cacheKey);
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error accessing cache (List): key={}", cacheKey, e);
            return null;
        }
    }
    
    /**
     * 패턴과 일치하는 모든 캐시 키들을 무효화
     * 
     * @param pattern 캐시 키 패턴 (예: "order:list:user_1_*")
     */
    @Override
    public void evictByPattern(String pattern) {
        String fullPattern = CACHE_KEY_PREFIX + pattern;
        
        try {
            // Redisson의 keys 메서드를 사용하여 패턴과 일치하는 키들 찾기
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(fullPattern);
            
            int count = 0;
            for (String key : keys) {
                redissonClient.getBucket(key).delete();
                count++;
            }
            
            log.debug("Cache evicted by pattern: pattern={}, evictedCount={}", fullPattern, count);
            
        } catch (Exception e) {
            log.error("Error evicting cache by pattern: pattern={}", fullPattern, e);
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