package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.event.*;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka를 통한 이벤트 발행 어댑터
 * 
 * EventPort 인터페이스를 구현하여 Kafka로 이벤트를 발행하고,
 * EventLog 테이블에 발행 이력을 저장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventAdapter implements EventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventLogRepositoryPort eventLogRepository;
    private final KeyGenerator keyGenerator;

    @Override
    public void publish(String topic, Object event) {
        String correlationId = keyGenerator.generateEventCorrelationId();
        
        log.debug("이벤트 발행 시작: topic={}, event={}, correlationId={}", 
                 topic, event.getClass().getSimpleName(), correlationId);
        
        // EventLog 사전 저장 (PENDING 상태)
        EventLog eventLog = createEventLog(topic, event, correlationId);
        EventLog savedEventLog = eventLogRepository.save(eventLog);
        
        try {
            // Kafka로 이벤트 발행
            String key = generateEventKey(event);
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            
            // 비동기 결과 처리
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // 성공 시 EventLog 상태 업데이트
                    updateEventLogStatus(savedEventLog.getId(), EventStatus.PUBLISHED, 
                                       "stream:" + topic, null);
                    
                    log.info("이벤트 발행 성공: topic={}, partition={}, offset={}, correlationId={}", 
                            topic, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset(), correlationId);
                } else {
                    // 실패 시 EventLog 상태 업데이트
                    updateEventLogStatus(savedEventLog.getId(), EventStatus.FAILED, 
                                       null, ex.getMessage());
                    
                    log.error("이벤트 발행 실패: topic={}, correlationId={}, error={}", 
                             topic, correlationId, ex.getMessage(), ex);
                }
            });
            
        } catch (Exception e) {
            // 동기 예외 처리
            updateEventLogStatus(savedEventLog.getId(), EventStatus.FAILED, null, e.getMessage());
            log.error("이벤트 발행 중 예외 발생: topic={}, correlationId={}", topic, correlationId, e);
            throw new RuntimeException("이벤트 발행 실패", e);
        }
    }

    /**
     * EventLog 레코드 생성
     */
    private EventLog createEventLog(String topic, Object event, String correlationId) {
        EventType eventType = mapEventType(event);
        String payload = serializeEvent(event);
        
        return EventLog.builder()
                .eventType(eventType)
                .correlationId(correlationId)
                .payload(payload)
                .status(EventStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 이벤트 객체를 EventType으로 매핑
     */
    private EventType mapEventType(Object event) {
        if (event instanceof OrderCompletedEvent) {
            return EventType.ORDER_COMPLETED;
        } else if (event instanceof PaymentCompletedEvent) {
            return EventType.PAYMENT_COMPLETED;
        } else if (event instanceof CouponRequestEvent) {
            return EventType.COUPON_REQUEST;
        } else if (event instanceof ProductUpdatedEvent) {
            ProductUpdatedEvent productEvent = (ProductUpdatedEvent) event;
            switch (productEvent.getEventType()) {
                case CREATED -> { return EventType.PRODUCT_CREATED; }
                case UPDATED -> { return EventType.PRODUCT_UPDATED; }
                case DELETED -> { return EventType.PRODUCT_DELETED; }
                default -> { return EventType.PRODUCT_UPDATED; }
            }
        } else if (event instanceof BalanceUpdatedEvent) {
            BalanceUpdatedEvent balanceEvent = (BalanceUpdatedEvent) event;
            return balanceEvent.getEventType() == BalanceUpdatedEvent.BalanceEventType.CHARGED 
                   ? EventType.BALANCE_CHARGED 
                   : EventType.BALANCE_DEDUCTED;
        } else if (event instanceof CouponIssuedEvent) {
            return EventType.COUPON_ISSUED;
        } else {
            log.warn("알 수 없는 이벤트 타입: {}", event.getClass().getSimpleName());
            return EventType.UNKNOWN;
        }
    }

    /**
     * 이벤트 객체를 JSON 문자열로 직렬화
     */
    private String serializeEvent(Object event) {
        try {
            // Jackson을 사용한 JSON 직렬화
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("이벤트 직렬화 실패: {}", event, e);
            return event.toString();
        }
    }

    /**
     * 이벤트별 파티션 키 생성
     * 
     * - 쿠폰 이벤트: userId 기반 파티셔닝 (동일 사용자 요청 순서 보장)
     * - 주문/결제 이벤트: orderId 기반 파티셔닝
     * - 기타: 이벤트 타입 기반
     */
    private String generateEventKey(Object event) {
        if (event instanceof CouponRequestEvent couponRequestEvent) {
            return "user:" + couponRequestEvent.getUserId();
        } else if (event instanceof CouponIssuedEvent couponEvent) {
            return "user:" + couponEvent.getUserId();
        } else if (event instanceof OrderCompletedEvent orderEvent) {
            return "order:" + orderEvent.getOrderId();
        } else if (event instanceof PaymentCompletedEvent paymentEvent) {
            return "payment:" + paymentEvent.getPaymentId();
        } else if (event instanceof ProductUpdatedEvent productEvent) {
            return "product:" + productEvent.getProductId();
        } else if (event instanceof BalanceUpdatedEvent balanceEvent) {
            return "user:" + balanceEvent.getUserId();
        } else {
            return "default:" + System.currentTimeMillis();
        }
    }

    /**
     * EventLog 상태 업데이트 (비동기)
     */
    private void updateEventLogStatus(Long eventLogId, EventStatus status, 
                                    String externalEndpoint, String errorMessage) {
        try {
            EventLog eventLog = eventLogRepository.findById(eventLogId).orElse(null);
            if (eventLog != null) {
                eventLog.updateStatus(status);
                if (externalEndpoint != null) {
                    eventLog.setExternalEndpoint(externalEndpoint);
                }
                if (errorMessage != null) {
                    eventLog.setErrorMessage(errorMessage);
                }
                eventLogRepository.save(eventLog);
                
                log.debug("EventLog 상태 업데이트: id={}, status={}", eventLogId, status);
            }
        } catch (Exception e) {
            log.error("EventLog 상태 업데이트 실패: id={}, status={}", eventLogId, status, e);
            // EventLog 업데이트 실패 시도 업무 처리는 진행
        }
    }
}