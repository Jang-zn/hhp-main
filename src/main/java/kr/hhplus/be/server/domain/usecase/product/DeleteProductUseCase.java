package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteProductUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 상품 삭제 (Write-Through 패턴)
     * 
     * 1. 기존 상품 조회 및 삭제 가능 여부 검증
     * 2. DB에서 상품 삭제
     * 3. 삭제 성공 시 캐시에서도 제거
     * 4. 관련 캐시 무효화
     */
    public void execute(Long productId) {
        log.debug("상품 삭제 요청: productId={}", productId);
        
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        
        try {
            // 1. 기존 상품 조회 및 검증
            Optional<Product> productOpt = productRepositoryPort.findById(productId);
            if (productOpt.isEmpty()) {
                throw new ProductException.NotFound();
            }
            
            Product existingProduct = productOpt.get();
            log.debug("삭제할 상품 조회 성공: productId={}", productId);
            
            // 2. 삭제 가능 여부 검증
            validateDeletable(existingProduct);
            
            // 3. DB에서 삭제
            productRepositoryPort.deleteById(productId);
            log.debug("상품 DB 삭제 성공: productId={}", productId);
            
            // 4. 캐시에서 제거
            removeProductCache(productId);
            
            // 5. 관련 캐시 무효화
            invalidateRelatedCaches(productId);
            
            log.info("상품 삭제 완료: productId={}", productId);
            
        } catch (ProductException e) {
            log.warn("상품 삭제 실패 - 상품 없음: productId={}", productId);
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("상품 삭제 실패 - 삭제 불가: productId={}, reason={}", productId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("상품 삭제 실패: productId={}", productId, e);
            throw new RuntimeException("상품 삭제에 실패했습니다.", e);
        }
    }
    
    /**
     * 상품 삭제 가능 여부 검증
     */
    private void validateDeletable(Product product) {
        // 예약된 재고가 있으면 삭제 불가
        if (product.getReservedStock() > 0) {
            throw new IllegalArgumentException("예약된 재고가 있는 상품은 삭제할 수 없습니다.");
        }
        
        // 추가 비즈니스 규칙이 있다면 여기에 구현
        // 예: 주문 내역이 있는 상품, 활성 상태인 상품 등
    }
    
    /**
     * 상품 캐시 제거
     */
    private void removeProductCache(Long productId) {
        try {
            String cacheKey = keyGenerator.generateProductCacheKey(productId);
            cachePort.evict(cacheKey);
            log.debug("상품 캐시 제거 성공: productId={}", productId);
        } catch (Exception e) {
            log.warn("상품 캐시 제거 실패: productId={}", productId, e);
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
            
            // 상품별 모든 캐시 무효화 (상품 통계 등)
            String productCachePattern = keyGenerator.generateProductCachePattern(productId);
            cachePort.evictByPattern(productCachePattern);
            
            log.debug("관련 캐시 무효화 완료: productId={}", productId);
        } catch (Exception e) {
            log.warn("관련 캐시 무효화 실패: productId={}", productId, e);
        }
    }
}