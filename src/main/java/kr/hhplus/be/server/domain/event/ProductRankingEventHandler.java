package kr.hhplus.be.server.domain.event;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRankingEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    @Async
    @EventListener
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.debug("Processing order completed event: orderId={}", event.getOrderId());
        
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
            
            for (OrderCompletedEvent.ProductOrderInfo productOrder : event.getProductOrders()) {
                Long productId = productOrder.getProductId();
                int quantity = productOrder.getQuantity();
                
                String productKey = keyGenerator.generateProductRankingKey(productId);
                cachePort.addProductScore(dailyRankingKey, productKey, quantity);
                
                log.debug("Updated product ranking: productId={}, quantity={}, date={}", 
                         productId, quantity, today);
            }
            
        } catch (Exception e) {
            log.error("Failed to update product ranking for order: orderId={}", 
                     event.getOrderId(), e);
        }
    }
}