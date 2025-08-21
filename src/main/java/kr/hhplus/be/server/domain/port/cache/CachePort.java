package kr.hhplus.be.server.domain.port.cache;

import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;

public interface CachePort {
    /**
     * 캐시에서 값을 조회 (저장하지 않음)
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @return 캐시된 값 또는 null
     */
    <T> T get(String key, Class<T> type);
    
    /**
     * List 타입 캐시 조회 (저장하지 않음)
     * 
     * @param key 캐시 키
     * @return 캐시된 List 또는 null
     */
    <T> List<T> getList(String key);
    
    void put(String key, Object value, int ttlSeconds);
    void evict(String key);
    
    /**
     * 패턴과 일치하는 모든 캐시 키들을 무효화
     * 
     * @param pattern 캐시 키 패턴 (예: "order:list:user_1_*")
     */
    void evictByPattern(String pattern);
    
    // ========================= 상품 랭킹 관련 메서드 =========================
    
    void addProductScore(String rankingKey, String productKey, int orderQuantity);
    
    List<Long> getTopProductsByOrder(String rankingKey, int limit);
    
    List<Long> getProductRanking(String rankingKey, int offset, int limit);
    
    // ========================= 선착순 쿠폰 관련 메서드 =========================
    
    long issueCouponAtomically(String couponCounterKey, String couponUserKey, long maxCount);
    
    long getCouponCount(String couponCounterKey);
    
    boolean hasCouponIssued(String couponUserKey);
} 