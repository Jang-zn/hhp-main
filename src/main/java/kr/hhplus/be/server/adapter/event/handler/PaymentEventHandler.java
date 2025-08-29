package kr.hhplus.be.server.adapter.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 처리자
 * 
 * PaymentCompletedEvent를 받아서 결제 완료 후 사후 처리를 담당합니다.
 * - 결제 완료 로깅 및 모니터링
 * - 향후 알림, 정산 등의 확장 기능을 위한 진입점
 * 
 * 참고: 실제 비즈니스 로직(잔액 차감, 재고 차감, 주문 완료)은 
 * OrderService.payOrder()에서 이미 처리됨
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventHandler {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 처리 가능한 이벤트 타입 확인
     */
    public boolean canHandle(String eventType) {
        return "PAYMENT_COMPLETED".equals(eventType);
    }
    
    /**
     * 결제 완료 이벤트 처리
     */
    public void handle(EventMessage eventMessage) {
        log.info("결제 도메인 이벤트 처리 시작: eventType={}, eventId={}", 
                eventMessage.getEventType(), eventMessage.getEventId());
        
        try {
            // PaymentCompletedEvent 파싱
            PaymentCompletedEvent paymentEvent = parsePaymentEvent(eventMessage);
            
            // 결제 완료 후 사후 처리
            // 실제 비즈니스 로직은 OrderService.payOrder()에서 이미 처리됨
            // 여기서는 모니터링, 알림 등 부가적인 처리를 수행
            
            log.info("결제 완료 확인 - 사후 처리: orderId={}, userId={}, amount={}", 
                    paymentEvent.getOrderId(), paymentEvent.getUserId(), paymentEvent.getAmount());
            
            // TODO: 향후 확장 가능한 기능들
            // - 결제 완료 알림 발송
            // - 정산 시스템 연동
            // - 회계 시스템 연동
            // - 성과 측정을 위한 지표 수집
            
            log.info("결제 도메인 이벤트 처리 완료: orderId={}, paymentId={}", 
                    paymentEvent.getOrderId(), paymentEvent.getPaymentId());
            
        } catch (Exception e) {
            log.error("결제 도메인 이벤트 처리 실패: eventId={}", eventMessage.getEventId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * EventMessage에서 PaymentCompletedEvent 파싱
     */
    private PaymentCompletedEvent parsePaymentEvent(EventMessage eventMessage) {
        try {
            Object payload = eventMessage.getPayload();
            String payloadJson = objectMapper.writeValueAsString(payload);
            return objectMapper.readValue(payloadJson, PaymentCompletedEvent.class);
        } catch (Exception e) {
            log.error("PaymentCompletedEvent 파싱 실패: eventId={}", eventMessage.getEventId(), e);
            throw new RuntimeException("PaymentCompletedEvent 파싱 실패", e);
        }
    }
}