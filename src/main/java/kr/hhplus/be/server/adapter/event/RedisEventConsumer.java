package kr.hhplus.be.server.adapter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import kr.hhplus.be.server.domain.enums.EventTopic;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.entity.Product;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis Streams Consumer
 * 
 * RedisEventAdapter에서 발행한 이벤트를 수신하여 실제 비즈니스 로직을 처리합니다.
 * 
 * 주요 기능:
 * - Consumer Group을 통한 안정적인 메시지 소비
 * - 메시지 ACK 처리
 * - 실패한 메시지에 대한 재시도
 * - 이벤트별 비즈니스 로직 처리 (랭킹 업데이트, 캐시 무효화 등)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "redis", matchIfMissing = true)
public class RedisEventConsumer {
    
    private final RedissonClient redissonClient;
    private final EventLogRepositoryPort eventLogRepository;
    private final ObjectMapper objectMapper;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    private static final String CONSUMER_GROUP = "hhplus-consumers";
    private static final String CONSUMER_NAME = "consumer-1";
    private static final int PRODUCT_CACHE_TTL = 3600; // 1시간
    
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
    public void consumeMessages() {
        try {
            // 1. 새로운 메시지 소비
            consumeNewMessages();
            
            // 2. 처리되지 않은 메시지 재시도
            retryPendingMessages();
            
        } catch (Exception e) {
            log.error("메시지 소비 중 오류 발생", e);
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
            
            log.debug("이벤트 메시지 처리 시작: eventId={}, type={}, correlationId={}", 
                     eventMessage.getEventId(), eventMessage.getEventType(), eventMessage.getCorrelationId());
            
            // EventLog 상태 업데이트 (처리 시작)
            updateEventLogStatus(eventMessage.getEventId(), EventStatus.IN_PROGRESS);
            
            // 이벤트 타입별 비즈니스 로직 처리
            processBusinessLogic(eventMessage);
            
            // 성공적으로 처리된 메시지 ACK
            acknowledgeMessage(streamKey, messageId);
            
            // EventLog 상태 업데이트 (처리 완료)
            updateEventLogStatus(eventMessage.getEventId(), EventStatus.COMPLETED);
            
            log.debug("이벤트 메시지 처리 완료: eventId={}, correlationId={}", 
                     eventMessage.getEventId(), eventMessage.getCorrelationId());
            
        } catch (Exception e) {
            log.error("메시지 처리 실패: stream={}, messageId={}", streamKey, messageId, e);
            
            // 실패한 메시지는 나중에 재시도될 수 있도록 ACK하지 않음
            // 단, 너무 많이 실패하면 Dead Letter Queue 로직 필요
        }
    }
    
    /**
     * 이벤트 타입별 비즈니스 로직을 처리합니다.
     */
    private void processBusinessLogic(EventMessage eventMessage) throws JsonProcessingException {
        Object payload = eventMessage.getPayload();
        String payloadJson = objectMapper.writeValueAsString(payload);
        
        // 이벤트 타입별 처리
        switch (eventMessage.getEventType()) {
            case "ORDER_COMPLETED", "ORDER_CREATED" -> {
                OrderCompletedEvent orderEvent = objectMapper.readValue(payloadJson, OrderCompletedEvent.class);
                handleOrderCompleted(orderEvent);
            }
            case "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED", "PRODUCT_STOCK_SYNC" -> {
                ProductUpdatedEvent productEvent = objectMapper.readValue(payloadJson, ProductUpdatedEvent.class);
                handleProductUpdated(productEvent);
            }
            default -> {
                log.debug("처리할 비즈니스 로직이 없는 이벤트 타입: {}", eventMessage.getEventType());
            }
        }
    }
    
    /**
     * 주문 완료 이벤트 처리 - 상품 랭킹 업데이트
     */
    private void handleOrderCompleted(OrderCompletedEvent event) {
        log.debug("Processing order completed event: orderId={}", event.getOrderId());
        
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
            
            for (OrderCompletedEvent.ProductOrderInfo productOrder : event.getProductOrders()) {
                Long productId = productOrder.getProductId();
                int quantity = productOrder.getQuantity();
                
                // addProductScore는 productId를 String으로 직접 전달 (productKey 생성 불필요)
                cachePort.addProductScore(dailyRankingKey, productId.toString(), quantity);
                
                log.debug("Updated product ranking: productId={}, quantity={}, date={}", 
                         productId, quantity, today);
            }
            
        } catch (Exception e) {
            log.error("Failed to update product ranking for order: orderId={}", 
                     event.getOrderId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * 상품 업데이트 이벤트 처리 - 캐시 무효화
     */
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
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    private void handleProductCreated(ProductUpdatedEvent event) {
        try {
            String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
            Product product = buildProductFromEvent(event);
            
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            log.debug("Product cached after creation: productId={}", event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to cache created product: productId={}", event.getProductId(), e);
            throw e;
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
            throw e;
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
            throw e;
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
            throw e;
        }
    }
    
    /**
     * 캐시 무효화 메서드들
     */
    private void invalidateProductListCaches() {
        try {
            String productListPattern = keyGenerator.generateProductListCachePattern();
            String popularProductPattern = keyGenerator.generatePopularProductCachePattern();
            
            cachePort.evictByPattern(productListPattern);
            cachePort.evictByPattern(popularProductPattern);
            
            log.debug("Product list caches invalidated");
        } catch (Exception e) {
            log.error("Failed to invalidate product list caches", e);
            throw e;
        }
    }
    
    private void invalidateOrderCaches(Long productId) {
        try {
            String orderCachePattern = keyGenerator.generateOrderCachePatternByProduct(productId);
            cachePort.evictByPattern(orderCachePattern);
            
            log.debug("Order caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate order caches for product: {}", productId, e);
            throw e;
        }
    }
    
    private void invalidateCouponCaches(Long productId) {
        try {
            String couponCachePattern = keyGenerator.generateCouponCachePatternByProduct(productId);
            cachePort.evictByPattern(couponCachePattern);
            
            log.debug("Coupon caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate coupon caches for product: {}", productId, e);
            throw e;
        }
    }
    
    private void invalidateRankingCaches() {
        try {
            String rankingPattern = keyGenerator.generateRankingCachePattern();
            cachePort.evictByPattern(rankingPattern);
            
            log.debug("Ranking caches invalidated");
        } catch (Exception e) {
            log.error("Failed to invalidate ranking caches", e);
            throw e;
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