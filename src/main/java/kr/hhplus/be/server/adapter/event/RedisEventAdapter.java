package kr.hhplus.be.server.adapter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.dto.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.redisson.api.RedissonClient;
import org.redisson.api.RStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "redis", matchIfMissing = true)
public class RedisEventAdapter implements EventPort {
    
    private final RedissonClient redissonClient;
    private final EventLogRepositoryPort eventLogRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    @Async("messagingExecutor")
    public void publish(String topic, Object event) {
        long startTime = System.currentTimeMillis();
        log.debug("Redis 이벤트 발행: topic={}, event={}", topic, event.getClass().getSimpleName());
        
        EventLog eventLog = null;
        try {
            // EventLog에 저장
            eventLog = saveEventLog(topic, event);
            
            // Redis Streams로 발행 (비즈니스 로직은 Consumer에서 처리)
            publishToRedisStream(eventLog, event);
            
            // 성공 시 상태 업데이트
            eventLog.updateStatus(EventStatus.PUBLISHED);
            eventLogRepository.save(eventLog);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Redis 이벤트 발행 성공: topic={}, duration={}ms", topic, duration);
            
            // 성능 모니터링: 느린 이벤트 발행 감지
            if (duration > 1000) {
                log.warn("Redis 이벤트 발행 지연 감지: topic={}, duration={}ms", topic, duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Redis 이벤트 발행 실패: topic={}, duration={}ms", topic, duration, e);
            updateEventLogOnFailure(eventLog, topic, e);
        }
    }
    
    
    private void publishToRedisStream(EventLog eventLog, Object event) throws JsonProcessingException {
        // 향상된 파티셔닝 전략: correlationId 기반으로 스트림 분산
        String streamKey = "stream:" + eventLog.getCorrelationId().substring(0, 8);
        
        long streamStartTime = System.currentTimeMillis();
        
        // EventMessage 객체 생성 (중복 생성 제거)
        EventMessage eventMessage = EventMessage.builder()
            .eventId(eventLog.getId())
            .correlationId(eventLog.getCorrelationId())
            .eventType(eventLog.getEventType().name())
            .payload(event)
            .timestamp(eventLog.getCreatedAt())
            .build();
            
        String fullEventData = objectMapper.writeValueAsString(eventMessage);
        
        try {
            // Redisson RStream은 단일 key-value 쌍만 지원
            RStream<String, Object> stream = redissonClient.getStream(streamKey);
            Object messageId = stream.add("event", fullEventData);
            
            eventLog.setExternalEndpoint(streamKey + ":" + messageId.toString());
            
            long streamDuration = System.currentTimeMillis() - streamStartTime;
            log.debug("Redis Streams 메시지 발행 완료: streamKey={}, messageId={}, duration={}ms", 
                     streamKey, messageId, streamDuration);
                     
            // Redis Streams 성능 모니터링
            if (streamDuration > 500) {
                log.warn("Redis Streams 발행 지연 감지: streamKey={}, duration={}ms", 
                        streamKey, streamDuration);
            }
            
        } catch (Exception e) {
            long streamDuration = System.currentTimeMillis() - streamStartTime;
            log.error("Redis Streams 발행 실패: streamKey={}, duration={}ms", 
                     streamKey, streamDuration, e);
            throw e; // 상위로 예외 전파
        }
    }
    
    private EventLog saveEventLog(String topic, Object event) {
        EventLog eventLog = EventLog.builder()
            .eventType(determineEventType(event))
            .payload(serializeEvent(event))
            .status(EventStatus.PENDING)
            .externalEndpoint(determineEndpoint(topic))
            .correlationId(generateCorrelationId())
            .build();
            
        return eventLogRepository.save(eventLog);
    }
    
    
    private EventType determineEventType(Object event) {
        // 이벤트 클래스명 기반으로 EventType 결정 (개선된 매핑)
        String className = event.getClass().getSimpleName();
        
        return switch (className) {
            case "OrderCompletedEvent" -> EventType.ORDER_CREATED;
            case "PaymentCompletedEvent" -> EventType.PAYMENT_COMPLETED;
            case "ProductUpdatedEvent" -> {
                // ProductUpdatedEvent는 내용에 따라 타입 결정
                if (event.toString().contains("created")) {
                    yield EventType.PRODUCT_STOCK_SYNC;
                } else {
                    yield EventType.PRODUCT_STOCK_SYNC;
                }
            }
            default -> {
                // 기존 로직 유지 (backward compatibility)
                if (className.contains("Order")) {
                    if (className.contains("Completed")) {
                        yield EventType.ORDER_CREATED;
                    }
                    yield EventType.ORDER_DATA_SYNC;
                } else if (className.contains("Payment")) {
                    if (className.contains("Completed")) {
                        yield EventType.PAYMENT_COMPLETED;
                    }
                    yield EventType.PAYMENT_DATA_SYNC;
                } else if (className.contains("Balance")) {
                    yield EventType.BALANCE_TRANSACTION_SYNC;
                } else if (className.contains("Product") || className.contains("Stock")) {
                    yield EventType.PRODUCT_STOCK_SYNC;
                } else if (className.contains("User")) {
                    yield EventType.USER_ACTIVITY_SYNC;
                }
                yield EventType.ORDER_DATA_SYNC; // 기본값
            }
        };
    }
    
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("이벤트 직렬화 실패: {}", event.getClass().getSimpleName(), e);
            return event.toString();
        }
    }
    
    private String determineEndpoint(String topic) {
        return "redis://streams/" + topic + ":async";
    }
    
    private String generateCorrelationId() {
        return "TXN-" + System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    
    private void updateEventLogOnFailure(EventLog eventLog, String topic, Exception e) {
        // 실패 처리 로직 (나중에 재시도할 수 있도록 정보 저장)
        log.error("이벤트 발행 실패로 인한 상태 업데이트 필요: topic={}", topic, e);
        
        if (eventLog != null) {
            try {
                // EventLog 상태를 FAILED로 업데이트하여 추후 재처리 가능하도록 설정
                eventLog.updateStatus(EventStatus.FAILED);
                eventLogRepository.save(eventLog);
                
                log.debug("EventLog 실패 상태 업데이트 완료: eventId={}, correlationId={}", 
                         eventLog.getId(), eventLog.getCorrelationId());
            } catch (Exception updateException) {
                log.error("EventLog 실패 상태 업데이트 중 추가 오류 발생: eventId={}", 
                         eventLog != null ? eventLog.getId() : "null", updateException);
            }
        }
    }
    
}