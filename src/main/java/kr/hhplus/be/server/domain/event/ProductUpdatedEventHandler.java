package kr.hhplus.be.server.domain.event;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.enums.ProductEventType;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 상품 업데이트 이벤트 핸들러
 * 
 * Phase 4: 이벤트 기반 캐시 무효화 전략
 * 상품 변경 시 관련된 모든 도메인의 캐시를 적절히 무효화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductUpdatedEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    // 캐시 TTL (1시간)
    private static final int PRODUCT_CACHE_TTL = 3600;
    
    @Async
    @EventListener
    public void handleProductUpdated(ProductUpdatedEvent event) {
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
            // 이벤트 처리 실패가 비즈니스 로직에 영향을 주지 않도록 예외를 먹음
        }
    }
    
    /**
     * 상품 생성 처리
     * - 개별 상품 캐시만 저장
     * - 다른 캐시 무효화는 하지 않음 (성능 최적화)
     */
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
    
    /**
     * 상품 수정 처리
     * - 개별 상품 캐시 갱신
     * - 관련된 모든 캐시 무효화
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
            
            log.debug("Product caches invalidated after update: productId={}, priceChanged={}", 
                     productId, event.isPriceChanged());
            
        } catch (Exception e) {
            log.error("Failed to handle product update: productId={}", productId, e);
        }
    }
    
    /**
     * 재고 수정 처리
     * - 개별 상품 캐시 갱신
     * - 주문 관련 캐시만 무효화 (성능 최적화)
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
            
            log.debug("Product stock updated and order caches invalidated: productId={}", productId);
            
        } catch (Exception e) {
            log.error("Failed to handle stock update: productId={}", productId, e);
        }
    }
    
    /**
     * 상품 삭제 처리
     * - 모든 관련 캐시 무효화
     * - 개별 상품 캐시 완전 제거
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
            invalidateRankingCaches(); // 삭제 시에는 랭킹도 재계산 필요
            
            log.debug("All product-related caches invalidated after deletion: productId={}", productId);
            
        } catch (Exception e) {
            log.error("Failed to handle product deletion: productId={}", productId, e);
        }
    }
    
    /**
     * 상품 목록 관련 캐시 무효화
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
        }
    }
    
    /**
     * 주문 관련 캐시 무효화
     */
    private void invalidateOrderCaches(Long productId) {
        try {
            String orderCachePattern = keyGenerator.generateOrderCachePatternByProduct(productId);
            cachePort.evictByPattern(orderCachePattern);
            
            log.debug("Order caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate order caches for product: {}", productId, e);
        }
    }
    
    /**
     * 쿠폰 관련 캐시 무효화
     */
    private void invalidateCouponCaches(Long productId) {
        try {
            String couponCachePattern = keyGenerator.generateCouponCachePatternByProduct(productId);
            cachePort.evictByPattern(couponCachePattern);
            
            log.debug("Coupon caches invalidated for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate coupon caches for product: {}", productId, e);
        }
    }
    
    /**
     * 랭킹 관련 캐시 무효화
     */
    private void invalidateRankingCaches() {
        try {
            String rankingPattern = keyGenerator.generateRankingCachePattern();
            cachePort.evictByPattern(rankingPattern);
            
            log.debug("Ranking caches invalidated");
        } catch (Exception e) {
            log.error("Failed to invalidate ranking caches", e);
        }
    }
    
    /**
     * 이벤트로부터 Product 엔티티 생성
     */
    private Product buildProductFromEvent(ProductUpdatedEvent event) {
        return Product.builder()
                .id(event.getProductId())
                .name(event.getProductName())
                .price(event.getPrice())
                .stock(event.getStock())
                .build();
    }
}