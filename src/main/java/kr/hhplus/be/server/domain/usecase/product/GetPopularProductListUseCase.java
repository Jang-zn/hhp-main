package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetPopularProductListUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 인기 상품 조회 (Redis 랭킹 + Cache-Aside 패턴)
     * 
     * 1. Redis 랭킹에서 상품 ID 목록 조회
     * 2. 각 상품을 개별 캐시에서 조회
     * 3. 캐시에 없는 상품은 DB에서 조회 후 캐시 저장
     * 4. Redis 랭킹이 비어있으면 DB 폴백
     */
    public List<Product> execute(int period, int limit, int offset) {
        log.debug("인기 상품 조회 요청: period={}, limit={}, offset={}", period, limit, offset);
        
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be greater than 0");
        }
        
        try {
            // 1. Redis 랭킹에서 상품 ID 목록 조회
            List<Long> rankedProductIds = getRankedProductIds(period, limit, offset);
            
            if (rankedProductIds.isEmpty()) {
                log.debug("Redis 랭킹이 비어있음, DB 폴백: period={}", period);
                return fallbackToDatabase(period, limit, offset);
            }
            
            // 2. 랭킹된 상품들을 캐시/DB에서 조회
            List<Product> products = getProductsByIds(rankedProductIds);
            
            log.debug("인기 상품 조회 성공: period={}, count={}", period, products.size());
            return products;
            
        } catch (Exception e) {
            log.error("인기 상품 조회 중 오류 발생, DB 폴백: period={}", period, e);
            return fallbackToDatabase(period, limit, offset);
        }
    }
    
    /**
     * Redis 랭킹에서 상품 ID 목록 조회
     */
    private List<Long> getRankedProductIds(int period, int limit, int offset) {
        try {
            // 기간에 따른 랭킹 키 선택 (현재는 일별 랭킹만 구현)
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String rankingKey = keyGenerator.generateDailyRankingKey(today);
            
            List<Long> productIds = cachePort.getProductRanking(rankingKey, offset, limit);
            log.debug("Redis 랭킹 조회 결과: key={}, count={}", rankingKey, productIds.size());
            return productIds;
            
        } catch (Exception e) {
            log.warn("Redis 랭킹 조회 실패: period={}", period, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 상품 ID 목록으로 상품 엔티티 조회 (캐시 우선)
     */
    private List<Product> getProductsByIds(List<Long> productIds) {
        List<Product> products = new ArrayList<>();
        
        for (Long productId : productIds) {
            try {
                Product product = getProductFromCacheOrDB(productId);
                if (product != null) {
                    products.add(product);
                }
            } catch (Exception e) {
                log.warn("상품 조회 실패, 건너뜀: productId={}", productId, e);
            }
        }
        
        return products;
    }
    
    /**
     * 개별 상품을 캐시에서 먼저 조회, 없으면 DB에서 조회
     */
    private Product getProductFromCacheOrDB(Long productId) {
        String cacheKey = keyGenerator.generateProductCacheKey(productId);
        
        try {
            // 캐시에서 먼저 조회
            Product cachedProduct = cachePort.get(cacheKey, Product.class);
            if (cachedProduct != null) {
                log.debug("상품 캐시 히트: productId={}", productId);
                return cachedProduct;
            }
            
            // 캐시 미스 - DB에서 조회
            log.debug("상품 캐시 미스, DB 조회: productId={}", productId);
            var productOpt = productRepositoryPort.findById(productId);
            
            if (productOpt.isPresent()) {
                Product product = productOpt.get();
                // DB 조회 성공 시 캐시에 저장
                try {
                    cachePort.put(cacheKey, product, CacheTTL.PRODUCT_INFO.getSeconds());
                    log.debug("상품 캐시 저장 성공: productId={}", productId);
                } catch (Exception cacheException) {
                    log.warn("상품 캐시 저장 실패: productId={}", productId, cacheException);
                }
                return product;
            }
            
            log.debug("상품 조회 결과 없음: productId={}", productId);
            return null;
            
        } catch (Exception e) {
            log.error("상품 조회 실패: productId={}", productId, e);
            return null;
        }
    }
    
    /**
     * Redis 랭킹 장애 시 DB로 폴백
     */
    private List<Product> fallbackToDatabase(int period, int limit, int offset) {
        try {
            String cacheKey = keyGenerator.generatePopularProductListCacheKey(period, limit, offset);
            
            // DB 폴백 결과도 캐시에서 먼저 확인
            List<Product> cachedProducts = cachePort.getList(cacheKey);
            if (cachedProducts != null && !cachedProducts.isEmpty()) {
                log.debug("인기 상품 캐시 히트 (DB 폴백): period={}, count={}", period, cachedProducts.size());
                return cachedProducts;
            }
            
            // DB에서 직접 조회
            List<Product> products = productRepositoryPort.findPopularProducts(period, limit, offset);
            
            // DB 조회 결과를 캐시에 저장 (기간별 동적 TTL 적용)
            if (!products.isEmpty()) {
                try {
                    int ttl = CacheTTL.getPopularProductTTLSeconds(period);
                    cachePort.put(cacheKey, products, ttl);
                    log.debug("인기 상품 DB 폴백 결과 캐시 저장: period={}, ttl={}초, count={}", period, ttl, products.size());
                } catch (Exception cacheException) {
                    log.warn("인기 상품 캐시 저장 실패: period={}", period, cacheException);
                }
            }
            
            return products;
            
        } catch (Exception e) {
            log.error("인기 상품 DB 폴백 실패: period={}", period, e);
            return new ArrayList<>();
        }
    }
} 