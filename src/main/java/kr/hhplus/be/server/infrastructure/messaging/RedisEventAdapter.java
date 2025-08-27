package kr.hhplus.be.server.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.messaging.EventPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.infrastructure.messaging.dto.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.redisson.api.RedissonClient;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "redis", matchIfMissing = true)
public class RedisEventAdapter implements EventPort {
    
    private final RedissonClient redissonClient;
    private final EventLogRepositoryPort eventLogRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    
    @Override
    public void publish(String topic, Object event) {
        log.debug("Redis 동기 이벤트 발행: topic={}, event={}", topic, event.getClass().getSimpleName());
        
        try {
            // EventLog에 저장
            EventLog eventLog = saveEventLog(topic, event, false);
            
            // 내부 이벤트 판단 (topic 기반)
            if (isInternalEvent(topic)) {
                // 내부 이벤트: ApplicationEventPublisher로 위임
                applicationEventPublisher.publishEvent(event);
                log.debug("내부 이벤트 ApplicationEventPublisher 위임: topic={}", topic);
            } else {
                // 외부 이벤트: Redis Streams로 처리
                publishToRedisStream(eventLog, event, true);
                log.debug("외부 이벤트 Redis Streams 발행: topic={}", topic);
            }
            
            // 성공 시 상태 업데이트
            eventLog.updateStatus(EventStatus.PUBLISHED);
            eventLogRepository.save(eventLog);
            
            log.debug("Redis 동기 이벤트 발행 성공: topic={}", topic);
            
        } catch (Exception e) {
            log.error("Redis 동기 이벤트 발행 실패: topic={}", topic, e);
            throw new RuntimeException("Redis publish failed", e);
        }
    }
    
    @Override
    @Async("messagingExecutor")
    public void publishAsync(String topic, Object event) {
        log.debug("Redis 비동기 이벤트 발행: topic={}, event={}", topic, event.getClass().getSimpleName());
        
        try {
            // EventLog에 저장 (외부 전송용)
            EventLog eventLog = saveEventLog(topic, event, true);
            
            // Redis Streams로 비동기 발행 (순서 보장, 재처리 가능)
            publishToRedisStream(eventLog, event, false);
            
            // 성공 시 상태 업데이트
            eventLog.updateStatus(EventStatus.PUBLISHED);
            eventLogRepository.save(eventLog);
            
            log.debug("Redis 비동기 이벤트 발행 성공: topic={}", topic);
            
        } catch (Exception e) {
            log.error("Redis 비동기 이벤트 발행 실패: topic={}", topic, e);
            // 비동기는 실패해도 비즈니스 로직에 영향 주지 않음
            updateEventLogOnFailure(topic, e);
        }
    }
    
    private void publishToRedisStream(EventLog eventLog, Object event, boolean isSync) throws JsonProcessingException {
        String streamKey = "stream:" + eventLog.getCorrelationId().substring(0, 8); // 간단한 파티셔닝
        
        // 모든 데이터를 하나의 JSON으로 직렬화
        EventMessage eventMessage = createEventMessage(eventLog, event);
        eventMessage = EventMessage.builder()
            .eventId(eventLog.getId())
            .correlationId(eventLog.getCorrelationId())
            .eventType(eventLog.getEventType().name())
            .payload(event)
            .timestamp(eventLog.getCreatedAt())
            .build();
            
        String fullEventData = objectMapper.writeValueAsString(eventMessage);
        
        // Redisson RStream은 단일 key-value 쌍만 지원
        RStream<String, Object> stream = redissonClient.getStream(streamKey);
        Object messageId = stream.add("event", fullEventData);
        
        eventLog.setExternalEndpoint(streamKey + ":" + messageId.toString());
        
        log.debug("Redis Streams 메시지 발행 완료: streamKey={}, messageId={}", streamKey, messageId);
    }
    
    private EventLog saveEventLog(String topic, Object event, boolean isAsync) {
        EventLog eventLog = EventLog.builder()
            .eventType(determineEventType(event))
            .payload(serializeEvent(event))
            .status(EventStatus.PENDING)
            .isExternal(isAsync || !isInternalEvent(topic)) // 비동기이거나 외부 토픽이면 외부 전송용
            .externalEndpoint(determineEndpoint(topic, isAsync))
            .correlationId(generateCorrelationId())
            .build();
            
        return eventLogRepository.save(eventLog);
    }
    
    private EventMessage createEventMessage(EventLog eventLog, Object event) {
        return EventMessage.builder()
            .eventId(eventLog.getId())
            .correlationId(eventLog.getCorrelationId())
            .eventType(eventLog.getEventType().name())
            .payload(event)
            .timestamp(eventLog.getCreatedAt())
            .build();
    }
    
    private EventType determineEventType(Object event) {
        // 이벤트 클래스명 기반으로 EventType 결정
        String className = event.getClass().getSimpleName();
        
        if (className.contains("Order")) {
            if (className.contains("Completed")) {
                return EventType.ORDER_CREATED;
            }
            return EventType.ORDER_DATA_SYNC;
        } else if (className.contains("Payment")) {
            if (className.contains("Completed")) {
                return EventType.PAYMENT_COMPLETED;
            }
            return EventType.PAYMENT_DATA_SYNC;
        } else if (className.contains("Balance")) {
            return EventType.BALANCE_TRANSACTION_SYNC;
        } else if (className.contains("Product") || className.contains("Stock")) {
            return EventType.PRODUCT_STOCK_SYNC;
        } else if (className.contains("User")) {
            return EventType.USER_ACTIVITY_SYNC;
        }   
        return EventType.ORDER_DATA_SYNC; // 기본값
    }
    
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("이벤트 직렬화 실패: {}", event.getClass().getSimpleName(), e);
            return event.toString();
        }
    }
    
    private String determineEndpoint(String topic, boolean isAsync) {
        return "redis://streams/" + topic + (isAsync ? ":async" : ":sync");
    }
    
    private String generateCorrelationId() {
        return "TXN-" + System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 내부 이벤트 여부 판단 (ApplicationEventPublisher 위임용)
     * 내부 이벤트: Spring 내부 처리 (랭킹 업데이트 등)
     * 외부 이벤트: Redis Streams 처리 (데이터 플랫폼 연동 등)
     */
    private boolean isInternalEvent(String topic) {
        return topic.startsWith("order.") ||
               topic.startsWith("product.") ||
               topic.startsWith("balance.") ||
               topic.startsWith("coupon.") ||
               topic.startsWith("internal.");
    }
    
    private void updateEventLogOnFailure(String topic, Exception e) {
        // 실패 처리 로직 (나중에 재시도할 수 있도록 정보 저장)
        log.error("이벤트 발행 실패로 인한 상태 업데이트 필요: topic={}", topic, e);
    }
    
}