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
        
        // 1. 캐시에서 먼저 조회 시도 (개별 예외 처리)
        Product cachedProduct = null;
        try {
            cachedProduct = cachePort.get(cacheKey, Product.class);
            if (cachedProduct != null) {
                log.debug("상품 캐시 히트: productId={}", productId);
                return Optional.of(cachedProduct);
            }
        } catch (Exception cacheException) {
            log.warn("캐시 조회 실패, DB로 진행: productId={}", productId, cacheException);
        }
        
        // 2. DB에서 조회 (재시도 포함)
        log.debug("상품 캐시 미스, DB 조회: productId={}", productId);
        Optional<Product> productOpt = null;
        
        try {
            productOpt = productRepositoryPort.findById(productId);
        } catch (Exception dbException) {
            // 일시적 오류일 수 있으므로 한 번 더 재시도
            log.warn("DB 조회 실패, 재시도: productId={}", productId, dbException);
            try {
                Thread.sleep(100); // 100ms 대기 후 재시도
                productOpt = productRepositoryPort.findById(productId);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("DB 조회 재시도 중 인터럽트 발생", ie);
            } catch (Exception retryException) {
                log.error("DB 조회 재시도 실패: productId={}", productId, retryException);
                throw retryException; // 재시도도 실패하면 예외 전파
            }
        }
        
        // 3. DB 조회 성공 시 캐시에 저장 (개별 예외 처리)
        if (productOpt != null && productOpt.isPresent()) {
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
        
        // 1. 캐시에서 먼저 조회 시도 (개별 예외 처리)
        List<Product> cachedProducts = null;
        try {
            cachedProducts = cachePort.getList(cacheKey);
            if (cachedProducts != null && !cachedProducts.isEmpty()) {
                log.debug("상품 목록 캐시 히트: limit={}, offset={}, count={}", limit, offset, cachedProducts.size());
                return cachedProducts;
            }
        } catch (Exception cacheException) {
            log.warn("캐시 조회 실패, DB로 진행: limit={}, offset={}", limit, offset, cacheException);
        }
        
        // 2. DB에서 조회 (재시도 포함)
        log.debug("상품 목록 캐시 미스, DB 조회: limit={}, offset={}", limit, offset);
        List<Product> products = null;
        
        try {
            products = productRepositoryPort.findAllWithPagination(limit, offset);
        } catch (Exception dbException) {
            // 일시적 오류일 수 있으므로 한 번 더 재시도
            log.warn("DB 조회 실패, 재시도: limit={}, offset={}", limit, offset, dbException);
            try {
                Thread.sleep(100); // 100ms 대기 후 재시도
                products = productRepositoryPort.findAllWithPagination(limit, offset);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("DB 조회 재시도 중 인터럽트 발생", ie);
            } catch (Exception retryException) {
                log.error("DB 조회 재시도 실패: limit={}, offset={}", limit, offset, retryException);
                throw retryException; // 재시도도 실패하면 예외 전파
            }
        }
        
        // 3. DB 조회 성공 시 캐시에 저장 (개별 예외 처리)
        if (products != null && !products.isEmpty()) {
            try {
                cachePort.put(cacheKey, products, CacheTTL.PRODUCT_LIST.getSeconds());
                log.debug("상품 목록 캐시 저장 성공: limit={}, offset={}, count={}", limit, offset, products.size());
            } catch (Exception cacheException) {
                log.warn("상품 목록 캐시 저장 실패, 계속 진행: limit={}, offset={}", limit, offset, cacheException);
            }
        }
        
        log.debug("상품 목록 조회 성공: count={}", products != null ? products.size() : 0);
        return products;
    }
    
    private void validatePaginationParams(int limit, int offset) {
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit exceeds maximum allowed (" + MAX_LIMIT + ")");
        }
    }
} 