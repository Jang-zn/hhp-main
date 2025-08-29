package kr.hhplus.be.server.adapter.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주문 이벤트 처리자
 * 
 * OrderCompletedEvent를 받아서 주문 관련 로직을 처리합니다.
 * - 상품 랭킹 업데이트
 * - 주문 완료 후 후속 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;
    
    /**
     * 처리 가능한 이벤트 타입 확인
     */
    public boolean canHandle(String eventType) {
        return eventType.startsWith("ORDER_");
    }
    
    /**
     * 주문 이벤트 처리
     */
    public void handle(EventMessage eventMessage) {
        log.info("주문 이벤트 처리 시작: eventType={}, eventId={}", 
                eventMessage.getEventType(), eventMessage.getEventId());
        
        try {
            // OrderCompletedEvent 파싱
            OrderCompletedEvent orderEvent = parseOrderEvent(eventMessage);
            
            // 이벤트 타입별 처리
            switch (eventMessage.getEventType()) {
                case "ORDER_COMPLETED", "ORDER_CREATED" -> {
                    handleOrderCompleted(orderEvent);
                }
                default -> {
                    log.debug("처리할 주문 이벤트 로직이 없음: {}", eventMessage.getEventType());
                }
            }
            
            log.info("주문 이벤트 처리 완료: orderId={}, eventType={}", 
                    orderEvent.getOrderId(), eventMessage.getEventType());
            
        } catch (Exception e) {
            log.error("주문 이벤트 처리 실패: eventId={}", eventMessage.getEventId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * 주문 완료 이벤트 처리 - 상품 랭킹 업데이트
     */
    private void handleOrderCompleted(OrderCompletedEvent event) {
        log.debug("주문 완료 이벤트 처리: orderId={}", event.getOrderId());
        
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
            
            // 주문된 각 상품의 랭킹 점수 업데이트
            for (OrderCompletedEvent.ProductOrderInfo productOrder : event.getProductOrders()) {
                Long productId = productOrder.getProductId();
                int quantity = productOrder.getQuantity();
                
                // addProductScore는 productId를 String으로 직접 전달
                cachePort.addProductScore(dailyRankingKey, productId.toString(), quantity);
                
                log.debug("상품 랭킹 업데이트: productId={}, quantity={}, date={}", 
                         productId, quantity, today);
            }
            
            log.debug("주문 완료 이벤트 처리 완료: orderId={}, productCount={}", 
                     event.getOrderId(), event.getProductOrders().size());
            
        } catch (Exception e) {
            log.error("주문 완료 이벤트 처리 실패: orderId={}", event.getOrderId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * EventMessage에서 OrderCompletedEvent 파싱
     */
    private OrderCompletedEvent parseOrderEvent(EventMessage eventMessage) {
        try {
            Object payload = eventMessage.getPayload();
            String payloadJson = objectMapper.writeValueAsString(payload);
            return objectMapper.readValue(payloadJson, OrderCompletedEvent.class);
        } catch (Exception e) {
            log.error("OrderCompletedEvent 파싱 실패: eventId={}", eventMessage.getEventId(), e);
            throw new RuntimeException("OrderCompletedEvent 파싱 실패", e);
        }
    }
}