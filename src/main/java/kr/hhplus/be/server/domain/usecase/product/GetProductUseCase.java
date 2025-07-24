package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetProductUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    
    private static final int MAX_LIMIT = 1000;
    
    /**
     * 단일 상품 조회
     */
    public Optional<Product> execute(Long productId) {
        log.debug("상품 조회 요청: productId={}", productId);
        
        if (productId == null) {
            log.warn("상품 ID가 null입니다");
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        
        try {
            String cacheKey = "product_" + productId;
            return cachePort.get(cacheKey, Optional.class, () -> 
                productRepositoryPort.findById(productId)
            );
        } catch (Exception e) {
            log.error("상품 조회 중 오류 발생: productId={}", productId, e);
            return productRepositoryPort.findById(productId);
        }
    }
    
    /**
     * 상품 목록 조회 (페이지네이션)
     */
    public List<Product> execute(int limit, int offset) {
        log.debug("상품 목록 조회 요청: limit={}, offset={}", limit, offset);
        
        validatePaginationParams(limit, offset);
        
        try {
            String cacheKey = "product_list_" + limit + "_" + offset;
            
            return cachePort.get(cacheKey, List.class, () -> 
                productRepositoryPort.findAllWithPagination(limit, offset)
            );
        } catch (Exception e) {
            log.error("상품 목록 조회 중 오류 발생: limit={}, offset={}", limit, offset, e);
            return productRepositoryPort.findAllWithPagination(limit, offset);
        }
    }
    
    private void validatePaginationParams(int limit, int offset) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit exceeds maximum allowed (" + MAX_LIMIT + ")");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
    }
} 