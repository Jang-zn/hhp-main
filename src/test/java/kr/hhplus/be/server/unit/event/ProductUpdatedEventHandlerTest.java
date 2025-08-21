package kr.hhplus.be.server.unit.event;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import kr.hhplus.be.server.domain.event.ProductUpdatedEventHandler;
import kr.hhplus.be.server.domain.enums.ProductEventType;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductUpdatedEventHandler 테스트
 * 
 * Phase 4: 이벤트 기반 캐시 무효화 전략 테스트
 * TDD 방식으로 먼저 테스트 작성 후 구현
 */
@DisplayName("상품 업데이트 이벤트 핸들러 테스트")
class ProductUpdatedEventHandlerTest {

    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    private ProductUpdatedEventHandler eventHandler;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventHandler = new ProductUpdatedEventHandler(cachePort, keyGenerator);
    }
    
    @Test
    @DisplayName("상품 생성 시 해당 상품만 캐시에 저장한다")
    void handleProductCreated_ShouldCacheNewProduct() {
        // given
        Long productId = 1L;
        String productName = "새 상품";
        BigDecimal price = new BigDecimal("10000");
        int stock = 100;
        
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.CREATED)
                .build();
                
        String productCacheKey = "product:" + productId;
        when(keyGenerator.generateProductCacheKey(productId)).thenReturn(productCacheKey);
        
        // when
        eventHandler.handleProductUpdated(event);
        
        // then
        verify(keyGenerator).generateProductCacheKey(productId);
        verify(cachePort).put(eq(productCacheKey), any(), eq(3600)); // 1시간 TTL
        
        // 생성 시에는 다른 캐시 무효화는 하지 않음
        verify(cachePort, never()).evictByPattern(anyString());
    }
    
    @Test
    @DisplayName("상품 수정 시 관련된 모든 캐시를 무효화한다")
    void handleProductUpdated_ShouldInvalidateRelatedCaches() {
        // given
        Long productId = 1L;
        String productName = "수정된 상품";
        BigDecimal price = new BigDecimal("15000"); // 가격 변경
        int stock = 50;
        
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.UPDATED)
                .build();
                
        String productCacheKey = "product:" + productId;
        String productListPattern = "product:list:*";
        String popularProductPattern = "product:popular:*";
        String orderCachePattern = "order:*:product_" + productId + "*";
        String rankingKey = "ranking:daily:*";
        
        when(keyGenerator.generateProductCacheKey(productId)).thenReturn(productCacheKey);
        when(keyGenerator.generateProductListCachePattern()).thenReturn(productListPattern);
        when(keyGenerator.generatePopularProductCachePattern()).thenReturn(popularProductPattern);
        when(keyGenerator.generateOrderCachePatternByProduct(productId)).thenReturn(orderCachePattern);
        when(keyGenerator.generateRankingCachePattern()).thenReturn(rankingKey);
        
        // when
        eventHandler.handleProductUpdated(event);
        
        // then
        // 1. 개별 상품 캐시 갱신
        verify(cachePort).put(eq(productCacheKey), any(), eq(3600));
        
        // 2. 상품 목록 관련 캐시 무효화
        verify(cachePort).evictByPattern(productListPattern);
        verify(cachePort).evictByPattern(popularProductPattern);
        
        // 3. 주문 관련 캐시 무효화 (가격 변경으로 인한 주문 검증 필요)
        verify(cachePort).evictByPattern(orderCachePattern);
        
        // 4. 랭킹 관련 캐시는 건드리지 않음 (주문 기반이므로)
        verify(cachePort, never()).evictByPattern(rankingKey);
    }
    
    @Test
    @DisplayName("상품 삭제 시 모든 관련 캐시를 무효화한다")
    void handleProductDeleted_ShouldInvalidateAllRelatedCaches() {
        // given
        Long productId = 1L;
        
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(productId)
                .eventType(ProductEventType.DELETED)
                .build();
                
        String productCacheKey = "product:" + productId;
        String productListPattern = "product:list:*";
        String popularProductPattern = "product:popular:*";
        String orderCachePattern = "order:*:product_" + productId + "*";
        String couponCachePattern = "coupon:*:product_" + productId + "*";
        String rankingKey = "ranking:daily:*";
        
        when(keyGenerator.generateProductCacheKey(productId)).thenReturn(productCacheKey);
        when(keyGenerator.generateProductListCachePattern()).thenReturn(productListPattern);
        when(keyGenerator.generatePopularProductCachePattern()).thenReturn(popularProductPattern);
        when(keyGenerator.generateOrderCachePatternByProduct(productId)).thenReturn(orderCachePattern);
        when(keyGenerator.generateCouponCachePatternByProduct(productId)).thenReturn(couponCachePattern);
        when(keyGenerator.generateRankingCachePattern()).thenReturn(rankingKey);
        
        // when
        eventHandler.handleProductUpdated(event);
        
        // then
        // 1. 개별 상품 캐시 완전 제거
        verify(cachePort).evict(productCacheKey);
        
        // 2. 모든 관련 도메인 캐시 무효화
        verify(cachePort).evictByPattern(productListPattern);
        verify(cachePort).evictByPattern(popularProductPattern);
        verify(cachePort).evictByPattern(orderCachePattern);
        verify(cachePort).evictByPattern(couponCachePattern);
        verify(cachePort).evictByPattern(rankingKey); // 삭제 시에는 랭킹도 재계산 필요
        
        // 삭제 시에는 캐시 저장하지 않음
        verify(cachePort, never()).put(anyString(), any(), anyInt());
    }
    
    @Test
    @DisplayName("재고만 변경된 경우 주문 관련 캐시만 무효화한다")
    void handleStockOnlyUpdate_ShouldInvalidateOrderCacheOnly() {
        // given
        Long productId = 1L;
        String productName = "기존 상품";
        BigDecimal price = new BigDecimal("10000"); // 가격 변경 없음
        int stock = 200; // 재고만 변경
        
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.STOCK_UPDATED)
                .build();
                
        String productCacheKey = "product:" + productId;
        String orderCachePattern = "order:*:product_" + productId + "*";
        
        when(keyGenerator.generateProductCacheKey(productId)).thenReturn(productCacheKey);
        when(keyGenerator.generateOrderCachePatternByProduct(productId)).thenReturn(orderCachePattern);
        
        // when
        eventHandler.handleProductUpdated(event);
        
        // then
        // 1. 개별 상품 캐시 갱신
        verify(cachePort).put(eq(productCacheKey), any(), eq(3600));
        
        // 2. 주문 관련 캐시만 무효화 (재고 변경으로 인한 주문 가능 여부 변경)
        verify(cachePort).evictByPattern(orderCachePattern);
        
        // 3. 목록/인기상품 캐시는 무효화하지 않음 (성능 최적화)
        verify(cachePort, never()).evictByPattern(contains("product:list"));
        verify(cachePort, never()).evictByPattern(contains("product:popular"));
    }
    
    @Test
    @DisplayName("이벤트 처리 중 예외 발생 시 로깅하고 계속 진행한다")
    void handleProductUpdated_ExceptionDuringCacheOperation_ShouldLogAndContinue() {
        // given
        Long productId = 1L;
        String productName = "테스트 상품";
        BigDecimal price = new BigDecimal("10000");
        int stock = 100;
        
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .stock(stock)
                .eventType(ProductEventType.UPDATED)
                .build();
                
        String productCacheKey = "product:" + productId;
        String productListPattern = "product:list:*";
        String orderCachePattern = "order:*:product_" + productId + "*";
        
        when(keyGenerator.generateProductCacheKey(productId)).thenReturn(productCacheKey);
        when(keyGenerator.generateProductListCachePattern()).thenReturn(productListPattern);
        when(keyGenerator.generateOrderCachePatternByProduct(productId)).thenReturn(orderCachePattern);
        
        // 캐시 저장 시 예외 발생 시뮬레이션
        doThrow(new RuntimeException("Redis connection failed"))
                .when(cachePort).put(anyString(), any(), anyInt());
        
        // when & then - 예외가 전파되지 않아야 함
        eventHandler.handleProductUpdated(event);
        
        // 예외가 발생해도 캐시 저장은 시도해야 함
        verify(cachePort).put(eq(productCacheKey), any(), eq(3600));
        // 예외 발생 후에도 무효화 작업은 시도하지 않음 (같은 try-catch 블록 내에 있음)
        verify(cachePort, never()).evictByPattern(anyString());
    }
    
    @Test
    @DisplayName("null 이벤트는 무시한다")
    void handleProductUpdated_NullEvent_ShouldIgnore() {
        // when & then - 예외가 발생하지 않아야 함
        eventHandler.handleProductUpdated(null);
        
        // 어떤 캐시 작업도 수행되지 않아야 함
        verifyNoInteractions(cachePort);
        verifyNoInteractions(keyGenerator);
    }
}