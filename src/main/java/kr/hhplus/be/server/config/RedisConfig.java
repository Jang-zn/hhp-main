package kr.hhplus.be.server.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.codec.JsonJacksonCodec;
import java.util.concurrent.TimeUnit;

/**
 * Redis 설정 클래스
 * 
 * Redisson 클라이언트 설정을 관리한다.
 * 분산 락과 캐싱을 모두 Redisson으로 통합하여 제공한다.
 */
@Configuration
public class RedisConfig {
    
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.data.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.data.redis.timeout:2000}")
    private int timeout;
    
    /**
     * Redisson 클라이언트 설정
     * 
     * 분산 락과 캐싱 모두에 사용
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        String redisAddress = String.format("redis://%s:%d", redisHost, redisPort);
        
        // 단일 서버 모드 설정
        config.useSingleServer()
            .setAddress(redisAddress)
            .setConnectionMinimumIdleSize(5)
            .setConnectionPoolSize(10)
            .setIdleConnectionTimeout(10000)
            .setConnectTimeout(timeout)
            .setTimeout(3000)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setSubscriptionsPerConnection(5)
            .setClientName("ecommerce-lock-client")
            .setSubscriptionConnectionMinimumIdleSize(1)
            .setSubscriptionConnectionPoolSize(5);
        
        // 비밀번호가 설정된 경우
        if (!redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }
        
        // 성능 최적화 설정
        config.setThreads(4);
        config.setNettyThreads(4);
        
        // JSON 코덱 설정 (캐싱용)
        config.setCodec(new JsonJacksonCodec());
        
        return Redisson.create(config);
    }
    
    /**
     * Redisson 기반 캐싱 유틸리티 메서드들
     * 기존 RedisTemplate 대체용
     */
    
    /**
     * 단일 값 캐싱
     */
    public <T> void setCacheValue(RedissonClient redisson, String key, T value, long ttl, TimeUnit timeUnit) {
        RBucket<T> bucket = redisson.getBucket(key);
        bucket.set(value, ttl, timeUnit);
    }
    
    /**
     * 단일 값 조회
     */
    public <T> T getCacheValue(RedissonClient redisson, String key) {
        RBucket<T> bucket = redisson.getBucket(key);
        return bucket.get();
    }
    
    /**
     * 캐시 삭제
     */
    public boolean deleteCacheValue(RedissonClient redisson, String key) {
        RBucket<Object> bucket = redisson.getBucket(key);
        return bucket.delete();
    }
    
    /**
     * Map 형태 캐싱
     */
    public <K, V> RMap<K, V> getCacheMap(RedissonClient redisson, String mapName) {
        return redisson.getMap(mapName);
    }
}