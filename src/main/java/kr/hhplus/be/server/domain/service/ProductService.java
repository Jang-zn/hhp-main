package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 상품 조회, 인기 상품 조회 등의 기능을 제공합니다.
 * 읽기 전용 작업으로 트랜잭션이 필요하지 않아 TransactionTemplate을 사용하지 않습니다.
 * 캐시 처리를 통해 성능을 최적화합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final GetProductUseCase getProductUseCase;
    private final GetPopularProductListUseCase getPopularProductListUseCase;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;


    /**
     * 단일 상품 조회 (캐시 적용)
     * 
     * @param productId 상품 ID
     * @return 상품 정보
     */
    public Optional<Product> getProduct(Long productId) {
        log.debug("상품 조회 요청: productId={}", productId);
        
        try {
            String cacheKey = keyGenerator.generateProductCacheKey(productId);
            
            // 캐시에서 조회 시도
            Product cachedProduct = cachePort.get(cacheKey, Product.class);
            
            if (cachedProduct != null) {
                log.debug("캐시에서 상품 조회 성공: productId={}", productId);
                return Optional.of(cachedProduct);
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            Optional<Product> productOpt = getProductUseCase.execute(productId);
            
            if (productOpt.isPresent()) {
                Product product = productOpt.get();
                log.debug("데이터베이스에서 상품 조회 성공: productId={}", productId);
                
                // TTL과 함께 캐시에 저장
                cachePort.put(cacheKey, product, CacheTTL.PRODUCT_DETAIL.getSeconds());
                
                return Optional.of(product);
            } else {
                log.debug("상품 조회 결과 없음: productId={}", productId);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("상품 조회 중 오류 발생: productId={}", productId, e);
            return getProductUseCase.execute(productId);
        }
    }
    
    /**
     * 상품 목록 조회 (캐시 적용)
     * 
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 상품 목록
     */
    public List<Product> getProductList(int limit, int offset) {
        log.debug("상품 목록 조회 요청: limit={}, offset={}", limit, offset);
        
        try {
            String cacheKey = keyGenerator.generateProductListCacheKey(limit, offset);
            
            // 캐시에서 조회 시도
            List<Product> cachedProducts = cachePort.getList(cacheKey);
            
            if (cachedProducts != null) {
                log.debug("캐시에서 상품 목록 조회 성공: count={}", cachedProducts.size());
                return cachedProducts;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            List<Product> products = getProductUseCase.execute(limit, offset);
            log.debug("데이터베이스에서 상품 목록 조회: count={}", products.size());
            
            // TTL과 함께 캐시에 저장
            cachePort.put(cacheKey, products, CacheTTL.PRODUCT_LIST.getSeconds());
            
            return products;
        } catch (Exception e) {
            log.error("상품 목록 조회 중 오류 발생: limit={}, offset={}", limit, offset, e);
            return getProductUseCase.execute(limit, offset);
        }
    }

    /**
     * 인기 상품 목록 조회 (캐시 적용)
     * 
     * @param period 기간 (일)
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 인기 상품 목록
     */
    public List<Product> getPopularProductList(int period, int limit, int offset) {
        log.debug("인기 상품 목록 조회 요청: period={}, limit={}, offset={}", period, limit, offset);
        
        try {
            String cacheKey = keyGenerator.generatePopularProductListCacheKey(period, limit, offset);
            
            // 캐시에서 조회 시도
            List<Product> cachedProducts = cachePort.getList(cacheKey);
            
            if (cachedProducts != null) {
                log.debug("캐시에서 인기 상품 목록 조회 성공: period={}, count={}", period, cachedProducts.size());
                return cachedProducts;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            List<Product> products = getPopularProductListUseCase.execute(period, limit, offset);
            log.debug("데이터베이스에서 인기 상품 목록 조회: period={}, count={}", period, products.size());
            
            // 동적 TTL과 함께 캐시에 저장
            int ttlSeconds = CacheTTL.getPopularProductTTLSeconds(period);
            cachePort.put(cacheKey, products, ttlSeconds);
            
            return products;
        } catch (Exception e) {
            log.error("인기 상품 목록 조회 중 오류 발생: period={}, limit={}, offset={}", period, limit, offset, e);
            return getPopularProductListUseCase.execute(period, limit, offset);
        }
    }
    
    /**
     * 상품 캐시 무효화
     * 
     * @param productId 상품 ID
     */
    public void invalidateProductCache(Long productId) {
        String cacheKey = keyGenerator.generateProductCacheKey(productId);
        cachePort.evict(cacheKey);
    }
}