package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주문 완료 이벤트 Consumer
 * 
 * 주문 완료 시 상품별 주문 수량을 Redis Sorted Set에 저장하여
 * 실시간 인기 상품 랭킹을 업데이트합니다.
 */
@Slf4j
@Service  
@RequiredArgsConstructor
public class OrderCompletedConsumer {

    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;

    @KafkaListener(
        topics = "order.completed",
        groupId = "order-ranking-group",
        containerFactory = "orderCompletedKafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(
            OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        log.info("주문 완료 이벤트 수신: partition={}, offset={}, orderId={}, userId={}, productCount={}", 
                partition, offset, event.getOrderId(), event.getUserId(), event.getProductOrders().size());
        
        try {
            // 현재 날짜의 랭킹 키 생성
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
            
            // 각 상품별로 랭킹 점수 업데이트
            for (OrderCompletedEvent.ProductOrderInfo productInfo : event.getProductOrders()) {
                Long productId = productInfo.getProductId();
                Integer quantity = productInfo.getQuantity();
                
                // Redis Sorted Set에 상품 점수 누적
                cachePort.addProductScore(dailyRankingKey, productId.toString(), quantity);
                
                log.debug("상품 랭킹 점수 업데이트: productId={}, quantity={}, key={}", 
                        productId, quantity, dailyRankingKey);
            }
            
            ack.acknowledge();
            
            log.info("주문 완료 이벤트 처리 완료: orderId={}, 랭킹 업데이트된 상품 수={}", 
                    event.getOrderId(), event.getProductOrders().size());
            
        } catch (Exception e) {
            log.error("주문 완료 이벤트 처리 실패: orderId={}, userId={}", 
                    event.getOrderId(), event.getUserId(), e);
            
            // 에러 상황에서도 ACK (무한 재시도 방지)
            ack.acknowledge();
        }
    }
}