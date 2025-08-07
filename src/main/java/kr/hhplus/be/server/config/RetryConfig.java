package kr.hhplus.be.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Retry 설정 클래스
 * 
 * 낙관적 락 충돌 시 재시도를 위한 설정을 제공합니다.
 * - OptimisticLockingFailureException 발생 시 자동 재시도
 * - 지수 백오프 정책으로 재시도 간격 조절
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * 낙관적 락 충돌용 RetryTemplate 설정
     * 
     * 설정:
     * - 최대 3회 재시도
     * - 초기 100ms 지연, 최대 1초, 지수 백오프 (100ms → 200ms → 400ms)
     * - OptimisticLockingFailureException에만 재시도 적용
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // 재시도 정책 설정
        retryTemplate.setRetryPolicy(retryPolicy());
        
        // 백오프 정책 설정 (재시도 간 지연시간)
        retryTemplate.setBackOffPolicy(backOffPolicy());
        
        return retryTemplate;
    }

    /**
     * 재시도 정책 설정
     * OptimisticLockingFailureException에 대해서만 최대 3회 재시도
     */
    @Bean
    public RetryPolicy retryPolicy() {
        Map<Class<? extends Throwable>, Boolean> exceptionsMap = new HashMap<>();
        exceptionsMap.put(OptimisticLockingFailureException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, exceptionsMap);
        return retryPolicy;
    }

    /**
     * 지수 백오프 정책 설정
     * 재시도 간격을 점진적으로 증가시켜 시스템 부하 감소
     */
    @Bean  
    public BackOffPolicy backOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        
        // 초기 지연 시간: 100ms
        backOffPolicy.setInitialInterval(100);
        
        // 최대 지연 시간: 1000ms (1초)
        backOffPolicy.setMaxInterval(1000);
        
        // 지수 승수: 2.0 (100ms → 200ms → 400ms → 800ms → 1000ms)
        backOffPolicy.setMultiplier(2.0);
        
        return backOffPolicy;
    }
}