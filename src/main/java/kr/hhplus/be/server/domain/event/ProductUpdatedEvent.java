package kr.hhplus.be.server.domain.event;

import kr.hhplus.be.server.domain.enums.ProductEventType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 업데이트 이벤트
 * 
 * Phase 4: 이벤트 기반 캐시 무효화 전략
 * 상품 생성/수정/삭제 시 발생하는 이벤트
 */
@Getter
@Builder
public class ProductUpdatedEvent {
    
    private final Long productId;
    private final String productName;
    private final BigDecimal price;
    private final Integer stock;
    private final ProductEventType eventType;
    private final LocalDateTime eventTime;
    
    // 이전 값들 (UPDATED 이벤트에서 변경사항 추적용)
    private final BigDecimal previousPrice;
    private final Integer previousStock;
    private final String previousName;
    
    /**
     * 상품 생성 이벤트 팩토리 메서드
     */
    public static ProductUpdatedEvent created(Long productId, String productName, 
                                            BigDecimal price, Integer stock) {
        return ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.CREATED)
                .eventTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 상품 수정 이벤트 팩토리 메서드
     */
    public static ProductUpdatedEvent updated(Long productId, String productName, 
                                            BigDecimal price, Integer stock,
                                            String previousName, BigDecimal previousPrice, 
                                            Integer previousStock) {
        return ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.UPDATED)
                .eventTime(LocalDateTime.now())
                .previousName(previousName)
                .previousPrice(previousPrice)
                .previousStock(previousStock)
                .build();
    }
    
    /**
     * 재고 수정 이벤트 팩토리 메서드
     */
    public static ProductUpdatedEvent stockUpdated(Long productId, String productName,
                                                 BigDecimal price, Integer stock, 
                                                 Integer previousStock) {
        return ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.STOCK_UPDATED)
                .eventTime(LocalDateTime.now())
                .previousStock(previousStock)
                .build();
    }
    
    /**
     * 상품 삭제 이벤트 팩토리 메서드
     */
    public static ProductUpdatedEvent deleted(Long productId) {
        return ProductUpdatedEvent.builder()
                .productId(productId)
                .eventType(ProductEventType.DELETED)
                .eventTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 가격이 변경되었는지 확인
     */
    public boolean isPriceChanged() {
        if (previousPrice == null || price == null) {
            return false;
        }
        return previousPrice.compareTo(price) != 0;
    }
    
    /**
     * 재고가 변경되었는지 확인
     */
    public boolean isStockChanged() {
        if (previousStock == null || stock == null) {
            return false;
        }
        return !previousStock.equals(stock);
    }
    
    /**
     * 상품명이 변경되었는지 확인
     */
    public boolean isNameChanged() {
        if (previousName == null || productName == null) {
            return false;
        }
        return !previousName.equals(productName);
    }
    
    /**
     * 캐시 무효화가 필요한 이벤트인지 확인
     */
    public boolean requiresCacheInvalidation() {
        return eventType != null && 
               (eventType == ProductEventType.UPDATED || 
                eventType == ProductEventType.DELETED ||
                eventType == ProductEventType.STOCK_UPDATED);
    }
}