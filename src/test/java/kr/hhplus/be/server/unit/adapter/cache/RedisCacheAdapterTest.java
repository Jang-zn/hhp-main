package kr.hhplus.be.server.unit.adapter.cache;

import kr.hhplus.be.server.adapter.cache.RedisCacheAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 캐시 어댑터")
class RedisCacheAdapterTest {
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private RBucket<Object> rBucket;
    
    @Mock
    private RBucket<String> stringBucket;
    
    @Mock
    private Supplier<String> supplier;
    
    private RedisCacheAdapter redisCacheAdapter;
    
    @BeforeEach
    void setUp() {
        redisCacheAdapter = new RedisCacheAdapter(redissonClient);
    }
    
    @Test
    @DisplayName("캐시 히트 - 캐시된 값 반환")
    void get_cacheHit() {
        // Given
        String key = "test-key";
        String cachedValue = "cached-value";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(stringBucket);
        when(stringBucket.get()).thenReturn(cachedValue);
        
        // When
        String result = redisCacheAdapter.get(key, String.class, supplier);
        
        // Then
        assertThat(result).isEqualTo(cachedValue);
        verify(redissonClient).getBucket(cacheKey);
        verify(stringBucket).get();
        verify(supplier, never()).get(); // supplier는 호출되지 않아야 함
    }
    
    @Test
    @DisplayName("캐시 미스 - supplier에서 값 조회하여 캐시에 저장")
    void get_cacheMiss() {
        // Given
        String key = "test-key";
        String suppliedValue = "supplied-value";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(stringBucket);
        when(stringBucket.get()).thenReturn(null);
        when(supplier.get()).thenReturn(suppliedValue);
        
        // When
        String result = redisCacheAdapter.get(key, String.class, supplier);
        
        // Then
        assertThat(result).isEqualTo(suppliedValue);
        verify(redissonClient).getBucket(cacheKey);
        verify(stringBucket).get();
        verify(supplier).get();
        verify(stringBucket).set(suppliedValue); // 캐시에 저장되어야 함
    }
    
    @Test
    @DisplayName("캐시 미스 - supplier에서 null 반환 시 캐시에 저장하지 않음")
    void get_cacheMiss_supplierReturnsNull() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(stringBucket);
        when(stringBucket.get()).thenReturn(null);
        when(supplier.get()).thenReturn(null);
        
        // When
        String result = redisCacheAdapter.get(key, String.class, supplier);
        
        // Then
        assertThat(result).isNull();
        verify(supplier).get();
        verify(stringBucket, never()).set(any()); // null이므로 캐시에 저장하지 않음
    }
    
    @Test
    @DisplayName("캐시 조회 중 예외 발생 시 supplier에서 값 반환")
    void get_exception_fallbackToSupplier() {
        // Given
        String key = "test-key";
        String suppliedValue = "supplied-value";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(stringBucket);
        when(stringBucket.get()).thenThrow(new RuntimeException("Redis connection error"));
        when(supplier.get()).thenReturn(suppliedValue);
        
        // When
        String result = redisCacheAdapter.get(key, String.class, supplier);
        
        // Then
        assertThat(result).isEqualTo(suppliedValue);
        verify(supplier).get(); // 예외 발생 시 supplier로 폴백
    }
    
    @Test
    @DisplayName("캐시 저장 - TTL 설정")
    void put_withTTL() {
        // Given
        String key = "test-key";
        String value = "test-value";
        int ttlSeconds = 300;
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        
        // When
        redisCacheAdapter.put(key, value, ttlSeconds);
        
        // Then
        verify(redissonClient).getBucket(cacheKey);
        verify(rBucket).set(value, ttlSeconds, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("캐시 저장 - TTL 0 (무제한)")
    void put_withoutTTL() {
        // Given
        String key = "test-key";
        String value = "test-value";
        int ttlSeconds = 0;
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        
        // When
        redisCacheAdapter.put(key, value, ttlSeconds);
        
        // Then
        verify(redissonClient).getBucket(cacheKey);
        verify(rBucket).set(value); // TTL 없이 저장
        verify(rBucket, never()).set(eq(value), anyInt(), any(TimeUnit.class));
    }
    
    @Test
    @DisplayName("캐시 저장 중 예외 발생 처리")
    void put_exception() {
        // Given
        String key = "test-key";
        String value = "test-value";
        int ttlSeconds = 300;
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        doThrow(new RuntimeException("Redis error")).when(rBucket).set(any(), anyInt(), any(TimeUnit.class));
        
        // When & Then - 예외가 발생해도 메서드는 정상 종료되어야 함
        redisCacheAdapter.put(key, value, ttlSeconds);
        
        verify(rBucket).set(value, ttlSeconds, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("캐시 삭제 성공")
    void evict_success() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.delete()).thenReturn(true);
        
        // When
        redisCacheAdapter.evict(key);
        
        // Then
        verify(redissonClient).getBucket(cacheKey);
        verify(rBucket).delete();
    }
    
    @Test
    @DisplayName("캐시 삭제 실패 - 키가 존재하지 않음")
    void evict_keyNotFound() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.delete()).thenReturn(false);
        
        // When
        redisCacheAdapter.evict(key);
        
        // Then
        verify(rBucket).delete();
    }
    
    @Test
    @DisplayName("캐시 삭제 중 예외 발생 처리")
    void evict_exception() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.delete()).thenThrow(new RuntimeException("Redis error"));
        
        // When & Then - 예외가 발생해도 메서드는 정상 종료되어야 함
        redisCacheAdapter.evict(key);
        
        verify(rBucket).delete();
    }
    
    @Test
    @DisplayName("TTL과 함께 캐시 조회 및 저장")
    void getWithTTL_cacheMiss() {
        // Given
        String key = "test-key";
        String suppliedValue = "supplied-value";
        int ttlSeconds = 300;
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(stringBucket);
        when(stringBucket.get()).thenReturn(null);
        when(supplier.get()).thenReturn(suppliedValue);
        
        // When
        String result = redisCacheAdapter.getWithTTL(key, String.class, supplier, ttlSeconds);
        
        // Then
        assertThat(result).isEqualTo(suppliedValue);
        verify(supplier).get();
        verify(stringBucket).set(suppliedValue, ttlSeconds, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("캐시 키 존재 여부 확인 - 존재함")
    void exists_true() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.isExists()).thenReturn(true);
        
        // When
        boolean result = redisCacheAdapter.exists(key);
        
        // Then
        assertThat(result).isTrue();
        verify(rBucket).isExists();
    }
    
    @Test
    @DisplayName("캐시 키 존재 여부 확인 - 존재하지 않음")
    void exists_false() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.isExists()).thenReturn(false);
        
        // When
        boolean result = redisCacheAdapter.exists(key);
        
        // Then
        assertThat(result).isFalse();
        verify(rBucket).isExists();
    }
    
    @Test
    @DisplayName("캐시 키 존재 여부 확인 중 예외 발생")
    void exists_exception() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.isExists()).thenThrow(new RuntimeException("Redis error"));
        
        // When
        boolean result = redisCacheAdapter.exists(key);
        
        // Then
        assertThat(result).isFalse(); // 예외 시 false 반환
        verify(rBucket).isExists();
    }
    
    @Test
    @DisplayName("캐시 TTL 확인")
    void getTTL() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        long expectedTTL = 30000L; // 30초
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.remainTimeToLive()).thenReturn(expectedTTL);
        
        // When
        long result = redisCacheAdapter.getTTL(key);
        
        // Then
        assertThat(result).isEqualTo(expectedTTL);
        verify(rBucket).remainTimeToLive();
    }
    
    @Test
    @DisplayName("캐시 TTL 확인 중 예외 발생")
    void getTTL_exception() {
        // Given
        String key = "test-key";
        String cacheKey = "cache:" + key;
        
        when(redissonClient.getBucket(cacheKey)).thenReturn(rBucket);
        when(rBucket.remainTimeToLive()).thenThrow(new RuntimeException("Redis error"));
        
        // When
        long result = redisCacheAdapter.getTTL(key);
        
        // Then
        assertThat(result).isEqualTo(-2L); // 예외 시 -2 반환 (키 없음으로 처리)
        verify(rBucket).remainTimeToLive();
    }
}