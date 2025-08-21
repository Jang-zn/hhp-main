package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import kr.hhplus.be.server.common.util.KeyGenerator;
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
    private final KeyGenerator keyGenerator;
    
    private static final int MAX_LIMIT = 1000;
    
    /**
     * 단일 상품 조회 (Cache-Aside 패턴)
     * 
     * 1. 캐시에서 먼저 조회
     * 2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
     * 3. 캐시 장애 시 DB로 폴백
     */
    public Optional<Product> execute(Long productId) {
        log.debug("상품 조회 요청: productId={}", productId);
        
        if (productId == null) {
            log.warn("상품 ID가 null입니다");
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        
        String cacheKey = keyGenerator.generateProductCacheKey(productId);
        
        try {
            // 1. 캐시에서 먼저 조회 시도
            Product cachedProduct = cachePort.get(cacheKey, Product.class);
            if (cachedProduct != null) {
                log.debug("상품 캐시 히트: productId={}", productId);
                return Optional.of(cachedProduct);
            }
            
            // 2. 캐시 미스 - DB에서 조회
            log.debug("상품 캐시 미스, DB 조회: productId={}", productId);
            Optional<Product> productOpt = productRepositoryPort.findById(productId);
            
            // 3. DB 조회 성공 시 캐시에 저장
            if (productOpt.isPresent()) {
                try {
                    cachePort.put(cacheKey, productOpt.get(), CacheTTL.PRODUCT_INFO.getSeconds());
                    log.debug("상품 캐시 저장 성공: productId={}", productId);
                } catch (Exception cacheException) {
                    log.warn("상품 캐시 저장 실패, 계속 진행: productId={}", productId, cacheException);
                }
                log.debug("상품 조회 성공: productId={}", productId);
            } else {
                log.debug("상품 조회 결과 없음: productId={}", productId);
            }
            
            return productOpt;
            
        } catch (Exception e) {
            log.error("상품 조회 중 오류 발생, DB 폴백: productId={}", productId, e);
            // 캐시 장애 시 DB로 직접 폴백
            return productRepositoryPort.findById(productId);
        }
    }
    
    /**
     * 상품 목록 조회 (페이지네이션, Cache-Aside 패턴)
     * 
     * 1. 캐시에서 먼저 조회
     * 2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
     * 3. 캐시 장애 시 DB로 폴백
     */
    public List<Product> execute(int limit, int offset) {
        log.debug("상품 목록 조회 요청: limit={}, offset={}", limit, offset);
        
        validatePaginationParams(limit, offset);
        
        String cacheKey = keyGenerator.generateProductListCacheKey(limit, offset);
        
        try {
            // 1. 캐시에서 먼저 조회 시도
            List<Product> cachedProducts = cachePort.getList(cacheKey);
            if (cachedProducts != null && !cachedProducts.isEmpty()) {
                log.debug("상품 목록 캐시 히트: limit={}, offset={}, count={}", limit, offset, cachedProducts.size());
                return cachedProducts;
            }
            
            // 2. 캐시 미스 - DB에서 조회
            log.debug("상품 목록 캐시 미스, DB 조회: limit={}, offset={}", limit, offset);
            List<Product> products = productRepositoryPort.findAllWithPagination(limit, offset);
            
            // 3. DB 조회 성공 시 캐시에 저장
            if (!products.isEmpty()) {
                try {
                    cachePort.put(cacheKey, products, CacheTTL.PRODUCT_LIST.getSeconds());
                    log.debug("상품 목록 캐시 저장 성공: limit={}, offset={}, count={}", limit, offset, products.size());
                } catch (Exception cacheException) {
                    log.warn("상품 목록 캐시 저장 실패, 계속 진행: limit={}, offset={}", limit, offset, cacheException);
                }
            }
            
            log.debug("상품 목록 조회 성공: count={}", products.size());
            return products;
            
        } catch (Exception e) {
            log.error("상품 목록 조회 중 오류 발생, DB 폴백: limit={}, offset={}", limit, offset, e);
            // 캐시 장애 시 DB로 직접 폴백
            return productRepositoryPort.findAllWithPagination(limit, offset);
        }
    }
    
    private void validatePaginationParams(int limit, int offset) {
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit exceeds maximum allowed (" + MAX_LIMIT + ")");
        }
    }
} 