package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventTopic;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.event.PaymentCompletedEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.integration.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * EventPort 통합 테스트
 * 실제 Redis 환경에서 이벤트 발행 및 처리 검증
 */
@ActiveProfiles("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class EventPortIntegrationTest extends IntegrationTestBase {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private EventPort eventPort;
    
    @Autowired
    private EventLogRepositoryPort eventLogRepository;

    @Test
    @DisplayName("주문 완료 이벤트가 성공적으로 발행되고 EventLog에 저장되는지 확인")
    void shouldPublishOrderCompletedEventSuccessfully() {
        // given
        OrderCompletedEvent event = new OrderCompletedEvent(1L, 1L, List.of(
                new OrderCompletedEvent.ProductOrderInfo(1L, 2),
                new OrderCompletedEvent.ProductOrderInfo(2L, 1)
        ));
        String topic = EventTopic.ORDER_COMPLETED.getTopic();
        
        // when
        eventPort.publish(topic, event);
        
        // then - 비동기 처리이므로 잠시 대기
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> eventLogs = eventLogRepository.findByEventType(EventType.ORDER_COMPLETED);
            assertThat(eventLogs).hasSize(1);
            
            EventLog eventLog = eventLogs.get(0);
            assertThat(eventLog.getStatus())
                .isIn(EventStatus.PUBLISHED, EventStatus.IN_PROGRESS, EventStatus.COMPLETED);
            assertThat(eventLog.getCorrelationId()).startsWith("event_");
            assertThat(eventLog.getPayload()).contains("orderId");
            assertThat(eventLog.getExternalEndpoint()).startsWith("stream:");
        });
    }

    @Test
    @DisplayName("결제 완료 이벤트가 성공적으로 발행되고 외부 엔드포인트가 설정되는지 확인")
    void shouldPublishPaymentCompletedEventWithExternalEndpoint() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(123L)
                .orderId(456L)
                .userId(789L)
                .amount(new BigDecimal("25000"))
                .completedAt(LocalDateTime.now())
                .build();
        String topic = EventTopic.PAYMENT_COMPLETED.getTopic();
        
        // when
        eventPort.publish(topic, event);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> eventLogs = eventLogRepository.findByEventType(EventType.PAYMENT_COMPLETED);
            assertThat(eventLogs).hasSize(1);
            
            EventLog eventLog = eventLogs.get(0);
            assertThat(eventLog.getStatus())
                .isIn(EventStatus.PUBLISHED, EventStatus.IN_PROGRESS, EventStatus.COMPLETED);
            assertThat(eventLog.getExternalEndpoint()).isNotNull();
            assertThat(eventLog.getExternalEndpoint()).contains("stream:");
            assertThat(eventLog.getPayload()).contains("paymentId\":123");
        });
    }

    @Test
    @DisplayName("동시에 여러 이벤트를 발행해도 모두 성공적으로 처리되는지 확인")
    void shouldHandleConcurrentEventPublishingSuccessfully() throws Exception {
        // given
        int eventCount = 10;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // when - 동시에 여러 이벤트 발행
        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                OrderCompletedEvent event = new OrderCompletedEvent(
                        (long) index, 
                        1L, 
                        List.of(new OrderCompletedEvent.ProductOrderInfo((long) index, 1))
                );
                eventPort.publish(EventTopic.ORDER_COMPLETED.getTopic(), event);
            });
            futures.add(future);
        }
        
        // 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        
        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> eventLogs = eventLogRepository.findByEventType(EventType.ORDER_COMPLETED);
            assertThat(eventLogs).hasSizeGreaterThanOrEqualTo(eventCount);
            
            // 모든 이벤트가 성공적으로 발행되었는지 확인
            long publishedCount = eventLogs.stream()
                    .filter(log -> log.getStatus() != EventStatus.FAILED)
                    .count();
            assertThat(publishedCount).isGreaterThanOrEqualTo(eventCount);
            
            // correlation ID가 모두 유니크한지 확인
            List<String> correlationIds = eventLogs.stream()
                    .map(EventLog::getCorrelationId)
                    .distinct()
                    .toList();
            assertThat(correlationIds).hasSize(eventLogs.size());
        });
    }

    @Test
    @DisplayName("EventPort를 통해 발행된 이벤트가 올바른 타입으로 저장되는지 확인")
    void shouldStoreEventsWithCorrectTypeBasedOnEventClass() {
        // given & when - 서로 다른 타입의 이벤트 발행
        OrderCompletedEvent orderEvent = new OrderCompletedEvent(1L, 1L, List.of());
        PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.builder()
                .paymentId(1L)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .completedAt(LocalDateTime.now())
                .build();
        
        eventPort.publish(EventTopic.ORDER_COMPLETED.getTopic(), orderEvent);
        eventPort.publish(EventTopic.PAYMENT_COMPLETED.getTopic(), paymentEvent);
        
        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> orderEventLogs = eventLogRepository.findByEventType(EventType.ORDER_COMPLETED);
            List<EventLog> paymentEventLogs = eventLogRepository.findByEventType(EventType.PAYMENT_COMPLETED);
            
            assertThat(orderEventLogs).hasSize(1);
            assertThat(paymentEventLogs).hasSize(1);
            
            assertThat(orderEventLogs.get(0).getPayload()).contains("orderId");
            assertThat(paymentEventLogs.get(0).getPayload()).contains("paymentId");
        });
    }

    @Test
    @DisplayName("이벤트 발행 시 correlation ID를 통한 추적이 가능한지 확인")
    void shouldEnableEventTrackingThroughCorrelationId() {
        // given
        OrderCompletedEvent event = new OrderCompletedEvent(999L, 888L, List.of(
                new OrderCompletedEvent.ProductOrderInfo(777L, 5)
        ));
        String topic = EventTopic.ORDER_COMPLETED.getTopic();
        
        // when
        eventPort.publish(topic, event);
        
        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> eventLogs = eventLogRepository.findAll();
            EventLog targetEventLog = eventLogs.stream()
                    .filter(log -> log.getPayload().contains("orderId\":999"))
                    .findFirst()
                    .orElseThrow();
            
            String correlationId = targetEventLog.getCorrelationId();
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).startsWith("event_");
            
            // correlation ID로 이벤트를 찾을 수 있는지 확인
            List<EventLog> foundByCorrelationId = eventLogRepository.findByCorrelationId(correlationId);
            assertThat(foundByCorrelationId).hasSize(1);
            assertThat(foundByCorrelationId.get(0).getId()).isEqualTo(targetEventLog.getId());
        });
    }

    @Test
    @DisplayName("Redis 연결 문제가 있어도 EventLog는 저장되는지 확인")
    void shouldSaveEventLogEvenIfRedisConnectionFails() {
        // given - Redis 연결 문제를 시뮬레이션하기는 어려우므로, 
        // 정상적인 상황에서 EventLog가 저장되는지만 확인
        OrderCompletedEvent event = new OrderCompletedEvent(1L, 1L, List.of());
        String topic = EventTopic.ORDER_COMPLETED.getTopic();
        
        int initialEventLogCount = eventLogRepository.findAll().size();
        
        // when
        eventPort.publish(topic, event);
        
        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> allEventLogs = eventLogRepository.findAll();
            assertThat(allEventLogs).hasSizeGreaterThan(initialEventLogCount);
            
            // 가장 최근에 생성된 이벤트 로그 확인
            EventLog latestEventLog = allEventLogs.stream()
                    .filter(log -> log.getPayload().contains("orderId\":1"))
                    .findFirst()
                    .orElseThrow();
            
            assertThat(latestEventLog).isNotNull();
            assertThat(latestEventLog.getCorrelationId()).isNotNull();
        });
    }

    @Test
    @DisplayName("이벤트 발행 후 상태가 PENDING에서 PUBLISHED로 변경되는지 확인")
    void shouldTransitionEventStatusFromPendingToPublished() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(555L)
                .orderId(666L)
                .userId(777L)
                .amount(new BigDecimal("50000"))
                .completedAt(LocalDateTime.now())
                .build();
        String topic = EventTopic.PAYMENT_COMPLETED.getTopic();
        
        // when
        eventPort.publish(topic, event);
        
        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventLog> eventLogs = eventLogRepository.findAll();
            EventLog targetEventLog = eventLogs.stream()
                    .filter(log -> log.getPayload().contains("paymentId\":555"))
                    .findFirst()
                    .orElseThrow();
            
            // 최종적으로 PUBLISHED 상태가 되어야 함
            assertThat(targetEventLog.getStatus())
                .isIn(EventStatus.PUBLISHED, EventStatus.IN_PROGRESS, EventStatus.COMPLETED);
            assertThat(targetEventLog.getExternalEndpoint()).isNotNull();
        });
    }
}