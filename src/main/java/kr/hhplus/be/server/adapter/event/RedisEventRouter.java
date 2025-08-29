package kr.hhplus.be.server.adapter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.adapter.event.handler.PaymentEventHandler;
import kr.hhplus.be.server.adapter.event.handler.ProductEventHandler;
import kr.hhplus.be.server.adapter.event.handler.OrderEventHandler;
import kr.hhplus.be.server.adapter.event.handler.CouponEventHandler;
import kr.hhplus.be.server.adapter.event.handler.BalanceEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * Redis Streams Event Router
 * 
 * RedisEventAdapter에서 발행한 이벤트를 수신하여 적절한 도메인 Handler에게 라우팅합니다.
 * 
 * 주요 기능:
 * - Consumer Group을 통한 안정적인 메시지 소비
 * - 메시지 ACK 처리
 * - 실패한 메시지에 대한 재시도
 * - 도메인별 EventHandler에게 라우팅
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "redis", matchIfMissing = true)
public class RedisEventRouter {
    
    private final RedissonClient redissonClient;
    private final EventLogRepositoryPort eventLogRepository;
    private final ObjectMapper objectMapper;
    private final PaymentEventHandler paymentEventHandler;
    private final ProductEventHandler productEventHandler;
    private final OrderEventHandler orderEventHandler;
    private final CouponEventHandler couponEventHandler;
    private final BalanceEventHandler balanceEventHandler;
    
    private static final String CONSUMER_GROUP = "hhplus-consumers";
    private static final String CONSUMER_NAME = "consumer-1";
    
    /**
     * 애플리케이션 시작 시 Consumer Group 초기화
     */
    @PostConstruct
    public void initializeConsumerGroup() {
        try {
            // 주요 스트림들에 대한 Consumer Group 생성
            createConsumerGroupForStream("stream:*");
            log.info("Redis Streams Consumer Group 초기화 완료: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            log.warn("Consumer Group 초기화 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }
    }
    
    /**
     * 정기적으로 Redis Streams에서 메시지를 소비합니다.
     * 5초마다 실행되며, 새로운 메시지와 처리되지 않은 메시지를 모두 확인합니다.
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @Async("messagingExecutor")
    public void routeMessages() { // 메서드명도 변경
        try {
            // 1. 새로운 메시지 소비
            consumeNewMessages();
            
            // 2. 처리되지 않은 메시지 재시도
            retryPendingMessages();
            
        } catch (Exception e) {
            log.error("메시지 라우팅 중 오류 발생", e);
        }
    }
    
    /**
     * 새로운 메시지를 소비합니다.
     */
    private void consumeNewMessages() {
        try {
            // 모든 stream 패턴에 대해 메시지 읽기
            for (int i = 0; i < 256; i++) { // 0-255 범위의 간단한 파티셔닝
                String streamKey = String.format("stream:%02x", i);
                
                try {
                    RStream<String, Object> stream = redissonClient.getStream(streamKey);
                    
                    // Consumer Group에서 새 메시지 읽기
                    Map<StreamMessageId, Map<String, Object>> messages = stream.readGroup(
                        CONSUMER_GROUP, CONSUMER_NAME,
                        StreamReadGroupArgs.greaterThan(StreamMessageId.NEWEST)
                            .count(10) // 한 번에 최대 10개 메시지
                    );
                    
                    if (!messages.isEmpty()) {
                        log.debug("새 메시지 수신: stream={}, count={}", streamKey, messages.size());
                        
                        for (Map.Entry<StreamMessageId, Map<String, Object>> entry : messages.entrySet()) {
                            processMessage(streamKey, entry.getKey(), entry.getValue());
                        }
                    }
                    
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                        // Consumer Group이 없으면 생성
                        createConsumerGroupForStream(streamKey);
                    } else if (e.getMessage() != null && !e.getMessage().contains("timeout")) {
                        log.warn("스트림 {} 메시지 읽기 실패: {}", streamKey, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("새 메시지 소비 중 오류", e);
        }
    }
    
    /**
     * 처리되지 않은 메시지를 재시도합니다.
     */
    private void retryPendingMessages() {
        try {
            for (int i = 0; i < 256; i++) {
                String streamKey = String.format("stream:%02x", i);
                
                try {
                    RStream<String, Object> stream = redissonClient.getStream(streamKey);
                    
                    // 30초 이상 처리되지 않은 메시지 조회
                    Map<StreamMessageId, Map<String, Object>> pendingMessages = 
                        stream.readGroup(CONSUMER_GROUP, CONSUMER_NAME,
                            StreamReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED)
                                .count(5) // 재시도는 적은 수로 제한
                        );
                    
                    if (!pendingMessages.isEmpty()) {
                        log.info("처리되지 않은 메시지 재시도: stream={}, count={}", 
                               streamKey, pendingMessages.size());
                        
                        for (Map.Entry<StreamMessageId, Map<String, Object>> entry : pendingMessages.entrySet()) {
                            processMessage(streamKey, entry.getKey(), entry.getValue());
                        }
                    }
                    
                } catch (Exception e) {
                    // 재시도는 조용히 실패 처리
                    log.debug("펜딩 메시지 재시도 실패: stream={}, error={}", streamKey, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("펜딩 메시지 재시도 중 오류", e);
        }
    }
    
    /**
     * 개별 메시지를 처리합니다.
     */
    private void processMessage(String streamKey, StreamMessageId messageId, Map<String, Object> message) {
        try {
            // 메시지에서 이벤트 데이터 추출
            String eventData = (String) message.get("event");
            if (eventData == null) {
                log.warn("이벤트 데이터가 없음: stream={}, messageId={}", streamKey, messageId);
                acknowledgeMessage(streamKey, messageId);
                return;
            }
            
            // JSON 파싱하여 EventMessage 객체 생성
            EventMessage eventMessage = objectMapper.readValue(eventData, EventMessage.class);
            
            log.debug("이벤트 메시지 라우팅 시작: eventId={}, type={}, correlationId={}", 
                     eventMessage.getEventId(), eventMessage.getEventType(), eventMessage.getCorrelationId());
            
            // EventLog 상태 업데이트 (처리 시작)
            updateEventLogStatus(eventMessage.getEventId(), EventStatus.IN_PROGRESS);
            
            // 적절한 Handler에게 라우팅
            routeToHandler(eventMessage);
            
            // 성공적으로 처리된 메시지 ACK
            acknowledgeMessage(streamKey, messageId);
            
            // EventLog 상태 업데이트 (처리 완료)
            updateEventLogStatus(eventMessage.getEventId(), EventStatus.COMPLETED);
            
            log.debug("이벤트 메시지 라우팅 완료: eventId={}, correlationId={}", 
                     eventMessage.getEventId(), eventMessage.getCorrelationId());
            
        } catch (Exception e) {
            log.error("메시지 라우팅 실패: stream={}, messageId={}", streamKey, messageId, e);
            
            // 실패한 메시지는 나중에 재시도될 수 있도록 ACK하지 않음
            // 단, 너무 많이 실패하면 Dead Letter Queue 로직 필요
        }
    }
    
    /**
     * 적절한 도메인 Handler에게 이벤트를 라우팅합니다.
     */
    private void routeToHandler(EventMessage eventMessage) {
        String eventType = eventMessage.getEventType();
        boolean handled = false;
        
        // PaymentEventHandler 처리 확인
        if (paymentEventHandler.canHandle(eventType)) {
            log.debug("이벤트 라우팅: {} -> PaymentEventHandler", eventType);
            paymentEventHandler.handle(eventMessage);
            handled = true;
        }
        
        // ProductEventHandler 처리 확인
        if (productEventHandler.canHandle(eventType)) {
            log.debug("이벤트 라우팅: {} -> ProductEventHandler", eventType);
            productEventHandler.handle(eventMessage);
            handled = true;
        }
        
        // OrderEventHandler 처리 확인
        if (orderEventHandler.canHandle(eventType)) {
            log.debug("이벤트 라우팅: {} -> OrderEventHandler", eventType);
            orderEventHandler.handle(eventMessage);
            handled = true;
        }
        
        // CouponEventHandler 처리 확인
        if (couponEventHandler.canHandle(eventType)) {
            log.debug("이벤트 라우팅: {} -> CouponEventHandler", eventType);
            couponEventHandler.handle(eventMessage);
            handled = true;
        }
        
        // BalanceEventHandler 처리 확인
        if (balanceEventHandler.canHandle(eventType)) {
            log.debug("이벤트 라우팅: {} -> BalanceEventHandler", eventType);
            balanceEventHandler.handle(eventMessage);
            handled = true;
        }
        
        if (!handled) {
            log.debug("처리할 수 있는 Handler가 없는 이벤트 타입: {}", eventType);
        }
    }
    
    /**
     * 메시지 ACK 처리
     */
    private void acknowledgeMessage(String streamKey, StreamMessageId messageId) {
        try {
            RStream<String, Object> stream = redissonClient.getStream(streamKey);
            stream.ack(CONSUMER_GROUP, messageId);
            log.debug("Message ACK: stream={}, messageId={}", streamKey, messageId);
        } catch (Exception e) {
            log.warn("Failed to ACK message: stream={}, messageId={}", streamKey, messageId, e);
        }
    }
    
    /**
     * EventLog 상태 업데이트
     */
    private void updateEventLogStatus(Long eventId, EventStatus status) {
        try {
            if (eventId != null) {
                EventLog eventLog = eventLogRepository.findById(eventId).orElse(null);
                if (eventLog != null) {
                    eventLog.updateStatus(status);
                    eventLogRepository.save(eventLog);
                    log.debug("EventLog 상태 업데이트: eventId={}, status={}", eventId, status);
                }
            }
        } catch (Exception e) {
            log.warn("EventLog 상태 업데이트 실패: eventId={}, status={}", eventId, status, e);
        }
    }
    
    /**
     * Consumer Group 생성
     */
    private void createConsumerGroupForStream(String streamKey) {
        try {
            RStream<String, Object> stream = redissonClient.getStream(streamKey);
            stream.createGroup(CONSUMER_GROUP, StreamMessageId.ALL);
            log.debug("Consumer Group 생성: stream={}, group={}", streamKey, CONSUMER_GROUP);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group이 이미 존재함: stream={}, group={}", streamKey, CONSUMER_GROUP);
            } else {
                log.warn("Consumer Group 생성 실패: stream={}, group={}", streamKey, CONSUMER_GROUP, e);
            }
        }
    }
}