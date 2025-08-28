package kr.hhplus.be.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 작업을 위한 스레드풀 설정
 * 
 * @Async 어노테이션 사용 시 기본 SimpleAsyncTaskExecutor 대신
 * 제한된 스레드풀을 사용하여 리소스 제어 및 성능 최적화
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 기본 비동기 실행기
     * 
     * 설정 이유:
     * - SimpleAsyncTaskExecutor는 매번 새 스레드 생성 (위험)
     * - ThreadPoolTaskExecutor로 스레드 수 제한 및 재사용
     * - 메모리 사용량 제어 및 성능 향상
     */
    @Override
    @Bean(name = "defaultExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 스레드풀 기본 설정
        executor.setCorePoolSize(8);           // 기본 스레드 수 (CPU 코어 수 기준)
        executor.setMaxPoolSize(20);           // 최대 스레드 수
        executor.setQueueCapacity(100);        // 큐 용량
        executor.setKeepAliveSeconds(60);      // 유휴 스레드 유지 시간
        
        // 디버깅용 스레드 이름 설정
        executor.setThreadNamePrefix("Async-");
        
        // 스레드풀 포화 시 처리 전략
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("Async executor rejected task: {}. " +
                    "Consider increasing pool size or queue capacity", r.toString());
        });
        
        executor.initialize();
        return executor;
    }

    /**
     * 비동기 작업에서 발생하는 예외 처리
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async method {} threw exception with params {}: {}", 
                     method.getName(), params, ex.getMessage(), ex);
        };
    }
}