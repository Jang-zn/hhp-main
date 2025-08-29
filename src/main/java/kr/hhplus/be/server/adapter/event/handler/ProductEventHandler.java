package kr.hhplus.be.server.adapter.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 상품 이벤트 처리자
 * 
 * ProductUpdatedEvent를 받아서 캐시 무효화 및 상품 관련 로직을 처리합니다.
 * - 상품 생성/수정/삭제 시 캐시 처리
 * - 재고 변경 시 관련 캐시 무효화
 * - 상품 목록 캐시 관리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;
    
    private static final int PRODUCT_CACHE_TTL = 3600; // 1시간
    
    /**
     * 처리 가능한 이벤트 타입 확인
     */
    public boolean canHandle(String eventType) {
        return eventType.startsWith("PRODUCT_");
    }
    
    /**
     * 상품 이벤트 처리
     */
    public void handle(EventMessage eventMessage) {
        log.info("상품 이벤트 처리 시작: eventType={}, eventId={}", 
                eventMessage.getEventType(), eventMessage.getEventId());
        
        try {
            // ProductUpdatedEvent 파싱
            ProductUpdatedEvent productEvent = parseProductEvent(eventMessage);
            
            // 이벤트 타입별 처리
            switch (productEvent.getEventType()) {
                case CREATED -> handleProductCreated(productEvent);
                case UPDATED -> handleProductFullUpdate(productEvent);
                case STOCK_UPDATED -> handleStockUpdated(productEvent);
                case DELETED -> handleProductDeleted(productEvent);
                default -> log.warn("알 수 없는 상품 이벤트 타입: {}", productEvent.getEventType());
            }
            
            log.info("상품 이벤트 처리 완료: productId={}, eventType={}", 
                    productEvent.getProductId(), productEvent.getEventType());
            
        } catch (Exception e) {
            log.error("상품 이벤트 처리 실패: eventId={}", eventMessage.getEventId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * 상품 생성 처리
     */
    private void handleProductCreated(ProductUpdatedEvent event) {
        try {
            String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
            Product product = buildProductFromEvent(event);
            
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            log.debug("상품 생성 후 캐시 저장: productId={}", event.getProductId());
            
        } catch (Exception e) {
            log.error("상품 생성 캐시 처리 실패: productId={}", event.getProductId(), e);
            throw e;
        }
    }
    
    /**
     * 상품 전체 업데이트 처리
     */
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
            
            log.debug("상품 업데이트 캐시 처리 완료: productId={}, priceChanged={}", 
                     productId, event.isPriceChanged());
            
        } catch (Exception e) {
            log.error("상품 업데이트 캐시 처리 실패: productId={}", productId, e);
            throw e;
        }
    }
    
    /**
     * 재고 업데이트 처리
     */
    private void handleStockUpdated(ProductUpdatedEvent event) {
        Long productId = event.getProductId();
        
        try {
            // 1. 개별 상품 캐시 갱신
            String productCacheKey = keyGenerator.generateProductCacheKey(productId);
            Product product = buildProductFromEvent(event);
            cachePort.put(productCacheKey, product, PRODUCT_CACHE_TTL);
            
            // 2. 주문 관련 캐시만 무효화 (재고 변경으로 인한 주문 가능 여부 변경)
            invalidateOrderCaches(productId);
            
            log.debug("재고 업데이트 캐시 처리 완료: productId={}", productId);
            
        } catch (Exception e) {
            log.error("재고 업데이트 캐시 처리 실패: productId={}", productId, e);
            throw e;
        }
    }
    
    /**
     * 상품 삭제 처리
     */
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
            
            log.debug("상품 삭제 후 모든 관련 캐시 무효화 완료: productId={}", productId);
            
        } catch (Exception e) {
            log.error("상품 삭제 캐시 처리 실패: productId={}", productId, e);
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
            
            log.debug("상품 목록 캐시 무효화 완료");
        } catch (Exception e) {
            log.error("상품 목록 캐시 무효화 실패", e);
            throw e;
        }
    }
    
    private void invalidateOrderCaches(Long productId) {
        try {
            String orderCachePattern = keyGenerator.generateOrderCachePatternByProduct(productId);
            cachePort.evictByPattern(orderCachePattern);
            
            log.debug("상품 관련 주문 캐시 무효화 완료: productId={}", productId);
        } catch (Exception e) {
            log.error("주문 캐시 무효화 실패: productId={}", productId, e);
            throw e;
        }
    }
    
    private void invalidateCouponCaches(Long productId) {
        try {
            String couponCachePattern = keyGenerator.generateCouponCachePatternByProduct(productId);
            cachePort.evictByPattern(couponCachePattern);
            
            log.debug("상품 관련 쿠폰 캐시 무효화 완료: productId={}", productId);
        } catch (Exception e) {
            log.error("쿠폰 캐시 무효화 실패: productId={}", productId, e);
            throw e;
        }
    }
    
    private void invalidateRankingCaches() {
        try {
            String rankingPattern = keyGenerator.generateRankingCachePattern();
            cachePort.evictByPattern(rankingPattern);
            
            log.debug("랭킹 캐시 무효화 완료");
        } catch (Exception e) {
            log.error("랭킹 캐시 무효화 실패", e);
            throw e;
        }
    }
    
    /**
     * Product 엔티티 빌더
     */
    private Product buildProductFromEvent(ProductUpdatedEvent event) {
        return Product.builder()
                .id(event.getProductId())
                .name(event.getProductName())
                .price(event.getPrice())
                .stock(event.getStock())
                .build();
    }
    
    /**
     * EventMessage에서 ProductUpdatedEvent 파싱
     */
    private ProductUpdatedEvent parseProductEvent(EventMessage eventMessage) {
        try {
            Object payload = eventMessage.getPayload();
            String payloadJson = objectMapper.writeValueAsString(payload);
            return objectMapper.readValue(payloadJson, ProductUpdatedEvent.class);
        } catch (Exception e) {
            log.error("ProductUpdatedEvent 파싱 실패: eventId={}", eventMessage.getEventId(), e);
            throw new RuntimeException("ProductUpdatedEvent 파싱 실패", e);
        }
    }
}