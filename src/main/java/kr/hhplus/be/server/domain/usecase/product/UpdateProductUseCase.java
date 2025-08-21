package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateProductUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 상품 수정 (Write-Through 패턴)
     * 
     * 1. 기존 상품 조회 및 검증
     * 2. DB에 상품 수정
     * 3. 수정 성공 시 캐시 업데이트
     * 4. 관련 캐시 무효화
     */
    public Product execute(Long productId, String name, BigDecimal price, Integer stock) {
        log.debug("상품 수정 요청: productId={}, name={}, price={}, stock={}", productId, name, price, stock);
        
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        
        try {
            // 1. 기존 상품 조회
            Optional<Product> productOpt = productRepositoryPort.findById(productId);
            if (productOpt.isEmpty()) {
                throw new ProductException.NotFound();
            }
            
            Product existingProduct = productOpt.get();
            log.debug("기존 상품 조회 성공: productId={}", productId);
            
            // 2. 수정할 필드만 업데이트 (새 Product 생성)
            String updatedName = existingProduct.getName();
            BigDecimal updatedPrice = existingProduct.getPrice();
            Integer updatedStock = existingProduct.getStock();
            
            if (name != null && !name.trim().isEmpty()) {
                validateName(name);
                updatedName = name;
            }
            
            if (price != null) {
                validatePrice(price);
                updatedPrice = price;
            }
            
            if (stock != null) {
                validateStock(stock, existingProduct.getReservedStock());
                updatedStock = stock;
            }
            
            Product updatedProduct = Product.builder()
                    .id(existingProduct.getId())
                    .version(existingProduct.getVersion())
                    .createdAt(existingProduct.getCreatedAt())
                    .updatedAt(existingProduct.getUpdatedAt())
                    .name(updatedName)
                    .price(updatedPrice)
                    .stock(updatedStock)
                    .reservedStock(existingProduct.getReservedStock())
                    .build();
            
            // 3. DB에 저장
            Product savedProduct = productRepositoryPort.save(updatedProduct);
            log.debug("상품 DB 수정 성공: productId={}", savedProduct.getId());
            
            // 4. 캐시 업데이트 (Write-Through)
            updateProductCache(savedProduct);
            
            // 5. 관련 캐시 무효화
            invalidateRelatedCaches(productId);
            
            return savedProduct;
            
        } catch (ProductException e) {
            log.warn("상품 수정 실패 - 상품 없음: productId={}", productId);
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("상품 수정 실패 - 잘못된 입력: productId={}, error={}", productId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("상품 수정 실패: productId={}", productId, e);
            throw new RuntimeException("상품 수정에 실패했습니다.", e);
        }
    }
    
    private void validateName(String name) {
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 비어있을 수 없습니다.");
        }
    }
    
    private void validatePrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
    }
    
    private void validateStock(int stock, int reservedStock) {
        if (stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }
        if (stock < reservedStock) {
            throw new IllegalArgumentException("재고는 예약된 재고보다 적을 수 없습니다.");
        }
    }
    
    /**
     * 상품 캐시 업데이트
     */
    private void updateProductCache(Product product) {
        try {
            String cacheKey = keyGenerator.generateProductCacheKey(product.getId());
            cachePort.put(cacheKey, product, CacheTTL.PRODUCT_INFO.getSeconds());
            log.debug("상품 캐시 업데이트 성공: productId={}", product.getId());
        } catch (Exception e) {
            log.warn("상품 캐시 업데이트 실패: productId={}", product.getId(), e);
        }
    }
    
    /**
     * 관련 캐시들 무효화
     */
    private void invalidateRelatedCaches(Long productId) {
        try {
            // 상품 목록 캐시 무효화
            String listCachePattern = keyGenerator.generateCustomCacheKey("product", "list", "*");
            cachePort.evictByPattern(listCachePattern);
            
            // 인기 상품 목록 캐시 무효화
            String popularCachePattern = keyGenerator.generatePopularProductCachePattern();
            cachePort.evictByPattern(popularCachePattern);
            
            log.debug("관련 캐시 무효화 완료: productId={}", productId);
        } catch (Exception e) {
            log.warn("관련 캐시 무효화 실패: productId={}", productId, e);
        }
    }
}