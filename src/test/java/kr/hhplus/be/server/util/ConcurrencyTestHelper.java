package kr.hhplus.be.server.util;

// Lombok 대체 구현

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 동시성 테스트를 위한 헬퍼 클래스
 * 
 * Why: 복잡한 동시성 테스트 설정을 단순화하고 결과 분석을 용이하게 하기 위함
 * How: ExecutorService와 CountDownLatch를 활용한 동시 실행 및 결과 수집
 */
public class ConcurrencyTestHelper {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConcurrencyTestHelper.class);

    /**
     * 동시성 테스트 결과를 담는 클래스
     * Why: 테스트 결과의 성공/실패 정보와 실행 시간 등을 구조화하여 제공
     */
    public static class ConcurrencyTestResult {
        private final int successCount;
        private final int failureCount;
        private final long executionTimeMs;
        private final List<String> errorMessages;
        
        public ConcurrencyTestResult(int successCount, int failureCount, long executionTimeMs, List<String> errorMessages) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.executionTimeMs = executionTimeMs;
            this.errorMessages = errorMessages;
        }
        
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public List<String> getErrorMessages() { return errorMessages; }
        
        public int getTotalCount() {
            return successCount + failureCount;
        }
        
        public double getSuccessRate() {
            return getTotalCount() == 0 ? 0.0 : (double) successCount / getTotalCount() * 100;
        }
    }

    /**
     * 지정된 개수의 스레드로 동일한 작업을 동시에 실행
     * 
     * Why: 실제 동시성 환경을 시뮬레이션하여 race condition, 데드락 등을 검증
     * How: CountDownLatch로 모든 스레드가 동시에 시작하도록 제어
     * 
     * @param threadCount 동시 실행할 스레드 개수
     * @param task 실행할 작업
     * @return 실행 결과
     */
    public static <T> ConcurrencyTestResult executeInParallel(int threadCount, Supplier<T> task) {
        return executeInParallel(threadCount, task, 10, TimeUnit.SECONDS);
    }

    /**
     * 타임아웃을 지정하여 동시 실행
     * 
     * Why: 데드락이나 무한 대기를 방지하기 위한 타임아웃 설정 필요
     */
    public static <T> ConcurrencyTestResult executeInParallel(
            int threadCount, 
            Supplier<T> task, 
            long timeout, 
            TimeUnit timeUnit) {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 모든 스레드 준비 완료 후 동시 시작
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        // 모든 스레드가 준비될 때까지 대기
                        startLatch.await();
                        
                        // 실제 작업 실행
                        T result = task.get();
                        successCount.incrementAndGet();
                        
                        log.debug("Thread-{} 성공: {}", threadIndex, result);
                        
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        synchronized (errorMessages) {
                            errorMessages.add(String.format("Thread-%d: %s", threadIndex, e.getMessage()));
                        }
                        log.debug("Thread-{} 실패: {}", threadIndex, e.getMessage());
                        
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            // 모든 스레드 동시 시작
            log.debug("{}개 스레드 동시 실행 시작", threadCount);
            startLatch.countDown();
            
            // 모든 스레드 완료 대기 (타임아웃 적용)
            boolean completed = doneLatch.await(timeout, timeUnit);
            if (!completed) {
                log.warn("일부 스레드가 타임아웃으로 완료되지 않았습니다");
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("동시성 테스트 완료 - 성공: {}, 실패: {}, 실행시간: {}ms", 
                successCount.get(), failureCount.get(), executionTime);
            
            return new ConcurrencyTestResult(
                successCount.get(), 
                failureCount.get(), 
                executionTime, 
                new ArrayList<>(errorMessages)
            );
            
        } catch (InterruptedException e) {
            log.error("동시성 테스트 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("동시성 테스트 실행 중 오류 발생", e);
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 서로 다른 작업을 동시에 실행 (데드락 테스트 등에 유용)
     * 
     * Why: 서로 다른 리소스에 대한 경합 상황을 시뮬레이션하기 위함
     */
    public static ConcurrencyTestResult executeMultipleTasks(List<Runnable> tasks) {
        return executeMultipleTasks(tasks, 10, TimeUnit.SECONDS);
    }

    public static ConcurrencyTestResult executeMultipleTasks(
            List<Runnable> tasks, 
            long timeout, 
            TimeUnit timeUnit) {
        
        int threadCount = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        try {
            for (int i = 0; i < tasks.size(); i++) {
                final int taskIndex = i;
                final Runnable task = tasks.get(i);
                
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        task.run();
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        synchronized (errorMessages) {
                            errorMessages.add(String.format("Task-%d: %s", taskIndex, e.getMessage()));
                        }
                        
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            boolean completed = doneLatch.await(timeout, timeUnit);
            
            if (!completed) {
                log.warn("일부 작업이 타임아웃으로 완료되지 않았습니다");
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new ConcurrencyTestResult(
                successCount.get(), 
                failureCount.get(), 
                executionTime, 
                new ArrayList<>(errorMessages)
            );
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("다중 작업 테스트 실행 중 오류 발생", e);
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 반복적인 부하 테스트 실행
     * 
     * Why: 지속적인 부하 상황에서의 시스템 안정성을 검증하기 위함
     */
    public static ConcurrencyTestResult executeLoadTest(
            Supplier<Void> task, 
            int totalExecutions, 
            int concurrentThreads) {
        
        return executeLoadTest(task, totalExecutions, concurrentThreads, 30, TimeUnit.SECONDS);
    }

    public static ConcurrencyTestResult executeLoadTest(
            Supplier<Void> task, 
            int totalExecutions, 
            int concurrentThreads,
            long timeout,
            TimeUnit timeUnit) {
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 지정된 횟수만큼 작업 제출
            for (int i = 0; i < totalExecutions; i++) {
                final int executionIndex = i;
                completionService.submit(() -> {
                    try {
                        task.get();
                        successCount.incrementAndGet();
                        return null;
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        synchronized (errorMessages) {
                            errorMessages.add(String.format("Execution-%d: %s", executionIndex, e.getMessage()));
                        }
                        return null;
                    }
                });
            }
            
            // 모든 작업 완료 대기
            for (int i = 0; i < totalExecutions; i++) {
                try {
                    completionService.poll(timeout, timeUnit);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("작업 {}이 인터럽트되었습니다", i);
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("부하 테스트 완료 - 총 실행: {}, 성공: {}, 실패: {}, 실행시간: {}ms", 
                totalExecutions, successCount.get(), failureCount.get(), executionTime);
            
            return new ConcurrencyTestResult(
                successCount.get(), 
                failureCount.get(), 
                executionTime, 
                new ArrayList<>(errorMessages)
            );
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}