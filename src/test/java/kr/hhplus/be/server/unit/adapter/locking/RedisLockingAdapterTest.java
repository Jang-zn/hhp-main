package kr.hhplus.be.server.unit.adapter.locking;

import kr.hhplus.be.server.adapter.locking.RedisLockingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 분산 락 어댑터")
class RedisLockingAdapterTest {
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private RLock rLock;
    
    private RedisLockingAdapter redisLockingAdapter;
    
    @BeforeEach
    void setUp() {
        redisLockingAdapter = new RedisLockingAdapter(redissonClient);
    }
    
    @Test
    @DisplayName("락 획득 성공")
    void acquireLock_success() throws InterruptedException {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        
        // When
        boolean result = redisLockingAdapter.acquireLock(key);
        
        // Then
        assertThat(result).isTrue();
        verify(redissonClient).getFairLock("lock:" + key);
        verify(rLock).tryLock(5L, 10L, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("락 획득 실패 - 타임아웃")
    void acquireLock_timeout() throws InterruptedException {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(false);
        
        // When
        boolean result = redisLockingAdapter.acquireLock(key);
        
        // Then
        assertThat(result).isFalse();
        verify(rLock).tryLock(5L, 10L, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("락 획득 중 인터럽트 발생")
    void acquireLock_interrupted() throws InterruptedException {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new InterruptedException("Interrupted"));
        
        // When
        boolean result = redisLockingAdapter.acquireLock(key);
        
        // Then
        assertThat(result).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        
        // 인터럽트 상태 복원
        Thread.interrupted();
    }
    
    @Test
    @DisplayName("락 해제 성공")
    void releaseLock_success() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        
        // When
        redisLockingAdapter.releaseLock(key);
        
        // Then
        verify(rLock).isHeldByCurrentThread();
        verify(rLock).unlock();
    }
    
    @Test
    @DisplayName("락 해제 시도 - 현재 스레드가 보유하지 않은 락")
    void releaseLock_notHeldByCurrentThread() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);
        
        // When
        redisLockingAdapter.releaseLock(key);
        
        // Then
        verify(rLock).isHeldByCurrentThread();
        verify(rLock, never()).unlock();
    }
    
    @Test
    @DisplayName("락 상태 확인 - 락이 걸려있음")
    void isLocked_true() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        
        // When
        boolean result = redisLockingAdapter.isLocked(key);
        
        // Then
        assertThat(result).isTrue();
        verify(rLock).isLocked();
    }
    
    @Test
    @DisplayName("락 상태 확인 - 락이 걸려있지 않음")
    void isLocked_false() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(false);
        
        // When
        boolean result = redisLockingAdapter.isLocked(key);
        
        // Then
        assertThat(result).isFalse();
        verify(rLock).isLocked();
    }
    
    @Test
    @DisplayName("커스텀 설정으로 락 획득")
    void acquireLockWithCustomSettings() throws InterruptedException {
        // Given
        String key = "test-key";
        long waitTime = 3L;
        long leaseTime = 5L;
        TimeUnit timeUnit = TimeUnit.SECONDS;
        
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.tryLock(waitTime, leaseTime, timeUnit)).thenReturn(true);
        
        // When
        boolean result = redisLockingAdapter.acquireLockWithCustomSettings(
            key, waitTime, leaseTime, timeUnit);
        
        // Then
        assertThat(result).isTrue();
        verify(rLock).tryLock(waitTime, leaseTime, timeUnit);
    }
    
    @Test
    @DisplayName("현재 스레드가 락 보유 여부 확인")
    void isHeldByCurrentThread() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        
        // When
        boolean result = redisLockingAdapter.isHeldByCurrentThread(key);
        
        // Then
        assertThat(result).isTrue();
        verify(rLock).isHeldByCurrentThread();
    }
    
    @Test
    @DisplayName("락 강제 해제")
    void forceUnlock() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        
        // When
        redisLockingAdapter.forceUnlock(key);
        
        // Then
        verify(rLock).forceUnlock();
    }
    
    @Test
    @DisplayName("락 해제 중 예외 발생 처리")
    void releaseLock_withException() {
        // Given
        String key = "test-key";
        when(redissonClient.getFairLock("lock:" + key)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("Unlock failed")).when(rLock).unlock();
        
        // When - 예외가 발생해도 메서드는 정상 종료
        redisLockingAdapter.releaseLock(key);
        
        // Then
        verify(rLock).isHeldByCurrentThread();
        verify(rLock).unlock();
    }
}