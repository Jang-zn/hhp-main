package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateProductUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 상품 생성 (Write-Through 패턴)
     * 
     * 1. DB에 상품 저장
     * 2. 저장 성공 시 캐시에도 저장
     * 3. 상품 목록 캐시 무효화
     */
    public Product execute(String name, BigDecimal price, int stock) {
        log.debug("상품 생성 요청: name={}, price={}, stock={}", name, price, stock);
        
        validateProductData(name, price, stock);
        
        try {
            // 1. DB에 상품 저장
            Product product = Product.builder()
                    .name(name)
                    .price(price)
                    .stock(stock)
                    .reservedStock(0)
                    .build();
            
            Product savedProduct = productRepositoryPort.save(product);
            log.debug("상품 DB 저장 성공: productId={}", savedProduct.getId());
            
            // 2. 캐시에 저장 (Write-Through)
            try {
                String cacheKey = keyGenerator.generateProductCacheKey(savedProduct.getId());
                cachePort.put(cacheKey, savedProduct, CacheTTL.PRODUCT_INFO.getSeconds());
                log.debug("상품 캐시 저장 성공: productId={}", savedProduct.getId());
            } catch (Exception cacheException) {
                log.warn("상품 캐시 저장 실패, 계속 진행: productId={}", savedProduct.getId(), cacheException);
            }
            
            // 3. 상품 목록 캐시 무효화
            invalidateProductListCaches();
            
            return savedProduct;
            
        } catch (Exception e) {
            log.error("상품 생성 실패: name={}, price={}, stock={}", name, price, stock, e);
            throw new RuntimeException("상품 생성에 실패했습니다.", e);
        }
    }
    
    private void validateProductData(String name, BigDecimal price, int stock) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }
    }
    
    /**
     * 상품 목록 관련 캐시들을 무효화
     */
    private void invalidateProductListCaches() {
        try {
            // 모든 상품 목록 캐시 패턴 무효화
            String listCachePattern = keyGenerator.generateCustomCacheKey("product", "list", "*");
            cachePort.evictByPattern(listCachePattern);
            
            // 인기 상품 목록 캐시 패턴 무효화  
            String popularCachePattern = keyGenerator.generatePopularProductCachePattern();
            cachePort.evictByPattern(popularCachePattern);
            
            log.debug("상품 목록 캐시 무효화 완료");
        } catch (Exception e) {
            log.warn("상품 목록 캐시 무효화 실패", e);
        }
    }
}