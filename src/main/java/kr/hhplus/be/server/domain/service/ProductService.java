package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.port.cache.CachePort;
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
    
    private static final int PRODUCT_CACHE_TTL = 600; // 10분
    private static final int PRODUCT_LIST_CACHE_TTL = 300; // 5분
    private static final int POPULAR_PRODUCT_CACHE_TTL = 300; // 5분

    /**
     * 단일 상품 조회 (캐시 적용)
     * 
     * @param productId 상품 ID
     * @return 상품 정보
     */
    public Optional<Product> getProduct(Long productId) {
        log.debug("상품 조회 요청: productId={}", productId);
        
        try {
            String cacheKey = "product_" + productId;
            Optional<Product> result = Optional.ofNullable(
                cachePort.get(cacheKey, Product.class, () -> {
                    Optional<Product> productOpt = getProductUseCase.execute(productId);
                    return productOpt.orElse(null);
                })
            );
            
            if (result.isPresent()) {
                log.debug("상품 조회 성공: productId={}", productId);
            } else {
                log.debug("상품 조회 결과 없음: productId={}", productId);
            }
            
            return result;
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
            String cacheKey = "product_list_" + limit + "_" + offset;
            return cachePort.getList(cacheKey, () -> {
                List<Product> products = getProductUseCase.execute(limit, offset);
                log.debug("데이터베이스에서 상품 목록 조회: count={}", products.size());
                return products;
            });
        } catch (Exception e) {
            log.error("상품 목록 조회 중 오류 발생: limit={}, offset={}", limit, offset, e);
            return getProductUseCase.execute(limit, offset);
        }
    }

    /**
     * 인기 상품 목록 조회 (캐시 적용)
     * 
     * @param period 기간 (일)
     * @return 인기 상품 목록
     */
    public List<Product> getPopularProductList(int period) {
        log.debug("인기 상품 목록 조회 요청: period={}", period);
        
        try {
            String cacheKey = "popular_products_" + period;
            return cachePort.getList(cacheKey, () -> {
                List<Product> products = getPopularProductListUseCase.execute(period);
                log.debug("데이터베이스에서 인기 상품 목록 조회: count={}", products.size());
                return products;
            });
        } catch (Exception e) {
            log.error("인기 상품 목록 조회 중 오류 발생: period={}", period, e);
            return getPopularProductListUseCase.execute(period);
        }
    }
    
    /**
     * 상품 캐시 무효화
     * 
     * @param productId 상품 ID
     */
    public void invalidateProductCache(Long productId) {
        try {
            String cacheKey = "product_" + productId;
            cachePort.evict(cacheKey);
            log.debug("상품 캐시 무효화: productId={}", productId);
        } catch (Exception e) {
            log.warn("상품 캐시 무효화 실패: productId={}, error={}", productId, e.getMessage());
        }
    }
}