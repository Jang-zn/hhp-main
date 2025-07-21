package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryEventLogRepository;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryEventLogRepository 단위 테스트")
class InMemoryEventLogRepositoryTest {

    private InMemoryEventLogRepository eventLogRepository;

    @BeforeEach
    void setUp() {
        eventLogRepository = new InMemoryEventLogRepository();
    }

    @Nested
    @DisplayName("이벤트 로그 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 이벤트 로그 저장")
        void save_Success() {
        // given
        EventLog eventLog = EventLog.builder()
                .id(1L)
                .eventType(EventType.ORDER_CREATED)
                .payload("{\"orderId\": 1, \"userId\": 100}")
                .status(EventStatus.PENDING)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
        assertThat(savedEventLog.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(savedEventLog.getPayload()).isEqualTo("{\"orderId\": 1, \"userId\": 100}");
        assertThat(savedEventLog.getStatus()).isEqualTo(EventStatus.PENDING);
    }

        @Test
        @DisplayName("성공케이스: 결제 완료 이벤트 로그 저장")
        void save_PaymentCompleted() {
        // given
        EventLog eventLog = EventLog.builder()
                .id(2L)
                .eventType(EventType.PAYMENT_COMPLETED)
                .payload("{\"paymentId\": 1, \"amount\": 100000}")
                .status(EventStatus.PUBLISHED)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
        assertThat(savedEventLog.getEventType()).isEqualTo(EventType.PAYMENT_COMPLETED);
            assertThat(savedEventLog.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryEventLogRepositoryTest#provideEventLogData")
        @DisplayName("성공케이스: 다양한 이벤트 로그 데이터로 저장")
        void save_WithDifferentEventData(EventType eventType, String payload, EventStatus status) {
        // given
        EventLog eventLog = EventLog.builder()
                .id(3L)
                .eventType(eventType)
                .payload(payload)
                .status(status)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getEventType()).isEqualTo(eventType);
            assertThat(savedEventLog.getPayload()).isEqualTo(payload);
            assertThat(savedEventLog.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("성공케이스: 빈 payload로 이벤트 로그 저장")
        void save_WithEmptyPayload() {
            // given
            EventLog eventLog = EventLog.builder()
                    .id(4L)
                    .eventType(EventType.ORDER_CREATED)
                    .payload("")
                    .status(EventStatus.PENDING)
                    .build();

            // when
            EventLog savedEventLog = eventLogRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getPayload()).isEmpty();
        }

        @Test
        @DisplayName("성공케이스: 긴 payload로 이벤트 로그 저장")
        void save_WithLongPayload() {
            // given
            String longPayload = "{".repeat(1000) + "\"data\": \"test\"" + "}".repeat(1000);
            EventLog eventLog = EventLog.builder()
                    .id(5L)
                    .eventType(EventType.PAYMENT_COMPLETED)
                    .payload(longPayload)
                    .status(EventStatus.PUBLISHED)
                    .build();

            // when
            EventLog savedEventLog = eventLogRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getPayload()).isEqualTo(longPayload);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 이벤트 로그 동시 저장")
        void save_ConcurrentSaveForDifferentEventLogs() throws Exception {
            // given
            int numberOfLogs = 20;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfLogs);
            AtomicInteger successCount = new AtomicInteger(0);
            // when - 서로 다른 이벤트 로그들을 동시에 저장
            for (int i = 0; i < numberOfLogs; i++) {
                final int logIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        EventLog eventLog = EventLog.builder()
                                .id((long) logIndex)
                                .eventType(logIndex % 2 == 0 ? EventType.ORDER_CREATED : EventType.PAYMENT_COMPLETED)
                                .payload("{\"id\": " + logIndex + ", \"data\": \"concurrent_test\"}")
                                .status(EventStatus.PENDING)
                                .build();
                        
                        eventLogRepository.save(eventLog);
                        Thread.sleep(1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error for log " + logIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then - 모든 이벤트 로그가 성공적으로 저장되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfLogs);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 이벤트 로그 동시 업데이트")
        void save_ConcurrentUpdateForSameEventLog() throws Exception {
            // given
            Long logId = 500L;
            EventLog initialLog = EventLog.builder()
                    .id(logId)
                    .eventType(EventType.ORDER_CREATED)
                    .payload("{\"initial\": true}")
                    .status(EventStatus.PENDING)
                    .build();
            eventLogRepository.save(initialLog);

            int numberOfThreads = 5;
            int updatesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successfulUpdates = new AtomicInteger(0);
            // when - 동일한 이벤트 로그를 동시에 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            EventLog updatedLog = EventLog.builder()
                                    .id(logId)
                                    .eventType(EventType.PAYMENT_COMPLETED)
                                    .payload("{\"thread\": " + threadId + ", \"update\": " + j + "}")
                                    .status(threadId % 2 == 0 ? EventStatus.PUBLISHED : EventStatus.PENDING)
                                    .build();
                            
                            eventLogRepository.save(updatedLog);
                            Thread.sleep(1);
                            successfulUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Update error for thread " + threadId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulUpdates.get()).isEqualTo(numberOfThreads * updatesPerThread);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            EventLog baseLog = EventLog.builder()
                    .id(600L)
                    .eventType(EventType.ORDER_CREATED)
                    .payload("{\"base\": \"log\"}")
                    .status(EventStatus.PUBLISHED)
                    .build();
            eventLogRepository.save(baseLog);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);
            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            List<EventLog> logs = eventLogRepository.findByStatus(EventStatus.PUBLISHED);
                            if (!logs.isEmpty()) {
                                successfulReads.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            EventLog newLog = EventLog.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .eventType(EventType.PAYMENT_COMPLETED)
                                    .payload("{\"writer\": " + writerId + ", \"index\": " + j + "}")
                                    .status(EventStatus.PENDING)
                                    .build();
                            
                            eventLogRepository.save(newLog);
                            Thread.sleep(1);
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 10);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> provideEventLogData() {
        return Stream.of(
                Arguments.of(EventType.ORDER_CREATED, "{\"orderId\": 1}", EventStatus.PENDING),
                Arguments.of(EventType.PAYMENT_COMPLETED, "{\"paymentId\": 1}", EventStatus.PUBLISHED),
                Arguments.of(EventType.ORDER_CREATED, "{\"orderId\": 2}", EventStatus.PUBLISHED)
        );
    }
}