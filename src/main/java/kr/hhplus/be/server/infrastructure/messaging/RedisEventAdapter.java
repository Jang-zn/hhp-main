package kr.hhplus.be.server.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.messaging.EventPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.enums.ProductEventType;
import kr.hhplus.be.server.infrastructure.messaging.dto.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.redisson.api.RedissonClient;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "redis", matchIfMissing = true)
public class RedisEventAdapter implements EventPort {
    
    private final RedissonClient redissonClient;
    private final EventLogRepositoryPort eventLogRepository;
    private final ObjectMapper objectMapper;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    @Override
    @Async("messagingExecutor")
    public void publish(String topic, Object event) {
        log.debug("Redis 이벤트 발행: topic={}, event={}", topic, event.getClass().getSimpleName());
        
        try {
            // EventLog에 저장
            EventLog eventLog = saveEventLog(topic, event);
            
            // 비즈니스 로직 처리
            handleEvent(topic, event);
            
            // Redis Streams로 처리
            publishToRedisStream(eventLog, event);
            
            // 성공 시 상태 업데이트
            eventLog.updateStatus(EventStatus.PUBLISHED);
            eventLogRepository.save(eventLog);
            
            log.debug("Redis 이벤트 발행 성공: topic={}", topic);
            
        } catch (Exception e) {
            log.error("Redis 이벤트 발행 실패: topic={}", topic, e);
            updateEventLogOnFailure(topic, e);
        }
    }
    
    
    private void publishToRedisStream(EventLog eventLog, Object event) throws JsonProcessingException {
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
    
    private String determineEndpoint(String topic) {
        return "redis://streams/" + topic + ":async";
    }
    
    private String generateCorrelationId() {
        return "TXN-" + System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    
    private void handleEvent(String topic, Object event) {
        try {
            if (topic.equals("order.completed") && event instanceof OrderCompletedEvent) {
                handleOrderCompleted((OrderCompletedEvent) event);
            } else if (topic.startsWith("product.") && event instanceof ProductUpdatedEvent) {
                handleProductUpdated((ProductUpdatedEvent) event);
            }
            // 추가 이벤트 처리는 여기에...
        } catch (Exception e) {
            log.error("이벤트 처리 실패: topic={}, event={}", topic, event.getClass().getSimpleName(), e);
        }
    }
    
    private void handleOrderCompleted(OrderCompletedEvent event) {
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
    
    private void handleProductUpdated(ProductUpdatedEvent event) {
        if (event == null) {
            log.warn("ProductUpdatedEvent is null, skipping");
            return;
        }
        
        log.debug("Processing product updated event: productId={}, eventType={}", 
                 event.getProductId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case CREATED -> handleProductCreated(event);
                case UPDATED -> handleProductFullUpdate(event);
                case STOCK_UPDATED -> handleStockUpdated(event);
                case DELETED -> handleProductDeleted(event);
                default -> log.warn("Unknown event type: {}", event.getEventType());
            }
            
            log.debug("Product updated event processed successfully: productId={}", 
                     event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to process product updated event: productId={}, eventType={}", 
                     event.getProductId(), event.getEventType(), e);
        }
    }
    
    // 캐시 TTL (1시간)
    private static final int PRODUCT_CACHE_TTL = 3600;
    
    private void handleProductCreated(ProductUpdatedEvent event) {
        try {
            String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
            Product product = buildProductFromEvent(event);
            
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            log.debug("Product cached after creation: productId={}", event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to cache created product: productId={}", event.getProductId(), e);
        }
    }
    
    private void handleProductFullUpdate(ProductUpdatedEvent event) {
        Long productId = event.getProductId();
        
        try {
            // 1. 개별 상품 캐시 갱신
            String productCacheKey = keyGenerator.generateProductCacheKey(productId);
            Product product = buildProductFromEvent(event);
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            
            // 2. 상품 목록 관련 캐시 무효화
            invalidateProductListCaches();
            
            // 3. 주문 관련 캐시 무효화 (가격 변경으로 인한 주문 검증 필요)
            invalidateOrderCaches(productId);
            
            // 4. 가격 변경 시 쿠폰 관련 캐시도 무효화
            if (event.isPriceChanged()) {
                invalidateCouponCaches(productId);
            }
            
            log.debug("Product caches invalidated after update: productId={}, priceChanged={}", 
                     productId, event.isPriceChanged());
            
        } catch (Exception e) {
            log.error("Failed to handle product update: productId={}", productId, e);
        }
    }
    
    private void handleStockUpdated(ProductUpdatedEvent event) {
        Long productId = event.getProductId();
        
        try {
            // 1. 개별 상품 캐시 갱신
            String productCacheKey = keyGenerator.generateProductCacheKey(productId);
            Product product = buildProductFromEvent(event);
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            
            // 2. 주문 관련 캐시만 무효화 (재고 변경으로 인한 주문 가능 여부 변경)
            invalidateOrderCaches(productId);
            
            log.debug("Product stock updated and order caches invalidated: productId={}", productId);
            
        } catch (Exception e) {
            log.error("Failed to handle stock update: productId={}", productId, e);
        }
    }
    
    private void handleProductDeleted(ProductUpdatedEvent event) {
        Long productId = event.getProductId();
        
        try {
            // 1. 개별 상품 캐시 완전 제거
            String productCacheKey = keyGenerator.generateProductCacheKey(productId);
            cachePort.evict(productCacheKey);
            
            // 2. 모든 관련 도메인 캐시 무효화
            invalidateProductListCaches();
            invalidateOrderCaches(productId);
            invalidateCouponCaches(productId);
            invalidateRankingCaches();
            
            log.debug("All product-related caches invalidated after deletion: productId={}", productId);
            
        } catch (Exception e) {
            log.error("Failed to handle product deletion: productId={}", productId, e);
        }
    }
    
    private void invalidateProductListCaches() {
        try {
            String productListPattern = keyGenerator.generateProductListCachePattern();
            String popularProductPattern = keyGenerator.generatePopularProductCachePattern();
            
            cachePort.evictByPattern(productListPattern);
            cachePort.evictByPattern(popularProductPattern);
            
            log.debug("Product list caches invalidated");
        } catch (Exception e) {
            log.error("Failed to invalidate product list caches", e);
        }
    }
    
    private void invalidateOrderCaches(Long productId) {
        try {
            String orderCachePattern = keyGenerator.generateOrderCachePatternByProduct(productId);
            cachePort.evictByPattern(orderCachePattern);
            
            log.debug("Order caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate order caches for product: {}", productId, e);
        }
    }
    
    private void invalidateCouponCaches(Long productId) {
        try {
            String couponCachePattern = keyGenerator.generateCouponCachePatternByProduct(productId);
            cachePort.evictByPattern(couponCachePattern);
            
            log.debug("Coupon caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate coupon caches for product: {}", productId, e);
        }
    }
    
    private void invalidateRankingCaches() {
        try {
            String rankingPattern = keyGenerator.generateRankingCachePattern();
            cachePort.evictByPattern(rankingPattern);
            
            log.debug("Ranking caches invalidated");
        } catch (Exception e) {
            log.error("Failed to invalidate ranking caches", e);
        }
    }
    
    private Product buildProductFromEvent(ProductUpdatedEvent event) {
        return Product.builder()
                .id(event.getProductId())
                .name(event.getProductName())
                .price(event.getPrice())
                .stock(event.getStock())
                .build();
    }
    
    private void updateEventLogOnFailure(String topic, Exception e) {
        // 실패 처리 로직 (나중에 재시도할 수 있도록 정보 저장)
        log.error("이벤트 발행 실패로 인한 상태 업데이트 필요: topic={}", topic, e);
    }
    
}