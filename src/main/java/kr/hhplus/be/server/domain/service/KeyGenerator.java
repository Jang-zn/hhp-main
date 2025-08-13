package kr.hhplus.be.server.domain.service;

import org.springframework.stereotype.Component;

/**
 * 분산락 및 캐시 키 생성기
 * 
 * 도메인별로 일관된 락 키 및 캐시 키 생성 전략을 제공한다.
 * 키 충돌을 방지하고 가독성을 높이기 위해 계층화된 구조를 사용한다.
 * 
 * 락 키 구조: {domain}:{identifier} (동일 리소스 통합)
 * 캐시 키 구조: {domain}:{type}:{identifier} (세분화된 관리)
 * 
 * 예시:
 * - 락: balance:user_1, product:product_1
 * - 캐시: balance:info:user_1, product:detail:product_1, product:popular:list
 */
@Component
public class KeyGenerator {
    
    // Domain prefixes
    private static final String BALANCE_DOMAIN = "balance";
    private static final String PRODUCT_DOMAIN = "product";
    private static final String ORDER_DOMAIN = "order";
    private static final String COUPON_DOMAIN = "coupon";
    private static final String PAYMENT_DOMAIN = "payment";
    
    // Resource types
    private static final String CREATE_RESOURCE = "create";
    private static final String USE_RESOURCE = "use";
    
    // Cache types
    private static final String INFO_TYPE = "info";        // 기본 정보 (상세 조회)
    private static final String LIST_TYPE = "list";        // 목록 조회
    private static final String POPULAR_TYPE = "popular";  // 인기 상품
    private static final String STATS_TYPE = "stats";      // 통계 정보
    private static final String HISTORY_TYPE = "history";  // 히스토리 정보
    
    private static final String SEPARATOR = ":";
    
    /**
     * 잔액 락 키 생성 (충전/차감 공통)
     * 사용자별로 모든 잔액 변경 작업을 순차적으로 처리하기 위한 키
     * 충전과 차감이 동시에 실행되지 않도록 같은 락 키 사용
     * 
     * @param userId 사용자 ID
     * @return 잔액 락 키 (예: balance:user_1)
     */
    public String generateBalanceKey(Long userId) {
        return String.join(SEPARATOR, BALANCE_DOMAIN, "user_" + userId);
    }
    
    
    /**
     * 상품 락 키 생성 (재고/예약 공통)
     * 상품별로 모든 재고 관련 작업을 순차적으로 처리하기 위한 키
     * 재고 변경과 예약이 동시에 실행되지 않도록 같은 락 키 사용
     * 
     * @param productId 상품 ID
     * @return 상품 락 키 (예: product:product_1)
     */
    public String generateProductKey(Long productId) {
        return String.join(SEPARATOR, PRODUCT_DOMAIN, "product_" + productId);
    }
    
    
    /**
     * 주문 생성 락 키 생성
     * 사용자별로 주문 생성을 순차적으로 처리하기 위한 키
     * 
     * @param userId 사용자 ID
     * @return 주문 생성 락 키 (예: order:create:user_1)
     */
    public String generateOrderCreateKey(Long userId) {
        return String.join(SEPARATOR, ORDER_DOMAIN, CREATE_RESOURCE, "user_" + userId);
    }
    
    /**
     * 주문 결제 락 키 생성
     * 주문별로 결제 처리를 순차적으로 하기 위한 키
     * 
     * @param orderId 주문 ID
     * @return 주문 결제 락 키 (예: payment:order:order_1)
     */
    public String generateOrderPaymentKey(Long orderId) {
        return String.join(SEPARATOR, PAYMENT_DOMAIN, "order", "order_" + orderId);
    }
    
    /**
     * 쿠폰 락 키 생성 (발급 관리용)
     * 쿠폰별로 발급 관련 작업을 순차적으로 처리하기 위한 키 (한정 수량 관리)
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 락 키 (예: coupon:coupon_1)
     */
    public String generateCouponKey(Long couponId) {
        return String.join(SEPARATOR, COUPON_DOMAIN, "coupon_" + couponId);
    }
    
    
    /**
     * 사용자별 쿠폰 사용 락 키 생성
     * 특정 사용자의 특정 쿠폰 사용을 관리하기 위한 키
     * (쿠폰 발급과는 별개의 락 - 사용자별 쿠폰 히스토리 관리)
     * 
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 사용자별 쿠폰 사용 락 키 (예: coupon:use:user_1_coupon_1)
     */
    public String generateCouponUseKey(Long userId, Long couponId) {
        return String.join(SEPARATOR, COUPON_DOMAIN, USE_RESOURCE, "user_" + userId + "_coupon_" + couponId);
    }
    
    /**
     * 복합 락 키 생성 (여러 리소스를 동시에 잠글 때)
     * 주문 생성 시 여러 상품의 재고를 동시에 차감할 때 사용
     * 
     * @param userId 사용자 ID
     * @param productIds 상품 ID 리스트 (정렬된 상태로 전달되어야 함)
     * @return 복합 락 키 (예: order:create_multi:user_1:products_1_2_3)
     */
    public String generateOrderCreateMultiProductKey(Long userId, Long... productIds) {
        java.util.Arrays.sort(productIds); // 데드락 방지를 위한 정렬
        String productList = java.util.Arrays.stream(productIds)
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining("_"));
        
        return String.join(SEPARATOR, ORDER_DOMAIN, "create_multi", "user_" + userId, "products_" + productList);
    }
    
    /**
     * 사용자 전체 락 키 생성 (긴급 상황 또는 관리 목적)
     * 사용자의 모든 활동을 일시적으로 중단할 때 사용
     * 
     * @param userId 사용자 ID
     * @return 사용자 전체 락 키 (예: user:global:user_1)
     */
    public String generateUserGlobalKey(Long userId) {
        return String.join(SEPARATOR, "user", "global", "user_" + userId);
    }
    
    /**
     * 커스텀 락 키 생성
     * 특별한 경우에 사용할 수 있는 일반적인 키 생성기
     * 
     * @param domain 도메인
     * @param resource 리소스
     * @param identifier 식별자
     * @return 커스텀 락 키
     */
    public String generateCustomKey(String domain, String resource, String identifier) {
        return String.join(SEPARATOR, domain, resource, identifier);
    }
    
    /**
     * 키에서 도메인 추출
     * 
     * @param lockKey 락 키
     * @return 도메인
     */
    public String extractDomain(String lockKey) {
        if (lockKey == null || !lockKey.contains(SEPARATOR)) {
            return null;
        }
        return lockKey.split(SEPARATOR)[0];
    }
    
    /**
     * 키 유효성 검증
     * 
     * @param lockKey 락 키
     * @return 유효성 여부
     */
    public boolean isValidKey(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = lockKey.split(SEPARATOR);
        return parts.length >= 2; // domain:identifier 최소 구조 (락 키는 2단계)
    }
    
    // ========================= 캐시 키 생성 메서드들 =========================
    
    /**
     * 사용자 잔액 정보 캐시 키 생성
     * 
     * @param userId 사용자 ID
     * @return 잔액 정보 캐시 키 (예: balance:info:user_1)
     */
    public String generateBalanceCacheKey(Long userId) {
        return String.join(SEPARATOR, BALANCE_DOMAIN, INFO_TYPE, "user_" + userId);
    }
    
    /**
     * 상품 상세 정보 캐시 키 생성
     * 
     * @param productId 상품 ID
     * @return 상품 상세 캐시 키 (예: product:info:product_1)
     */
    public String generateProductCacheKey(Long productId) {
        return String.join(SEPARATOR, PRODUCT_DOMAIN, INFO_TYPE, "product_" + productId);
    }
    
    /**
     * 인기 상품 목록 캐시 키 생성
     * 
     * @param limit 조회 개수
     * @return 인기 상품 목록 캐시 키 (예: product:popular:limit_10)
     */
    public String generatePopularProductListCacheKey(int limit) {
        return String.join(SEPARATOR, PRODUCT_DOMAIN, POPULAR_TYPE, "limit_" + limit);
    }
    
    /**
     * 상품 목록 캐시 키 생성
     * 
     * @param limit 조회 개수
     * @param offset 오프셋
     * @return 상품 목록 캐시 키 (예: product:list:limit_10_offset_0)
     */
    public String generateProductListCacheKey(int limit, int offset) {
        return String.join(SEPARATOR, PRODUCT_DOMAIN, LIST_TYPE, "limit_" + limit + "_offset_" + offset);
    }
    
    /**
     * 사용자 주문 목록 캐시 키 생성
     * 
     * @param userId 사용자 ID
     * @param limit 조회 개수
     * @param offset 오프셋
     * @return 주문 목록 캐시 키 (예: order:list:user_1_limit_10_offset_0)
     */
    public String generateOrderListCacheKey(Long userId, int limit, int offset) {
        return String.join(SEPARATOR, ORDER_DOMAIN, LIST_TYPE, 
            "user_" + userId + "_limit_" + limit + "_offset_" + offset);
    }
    
    /**
     * 주문 상세 정보 캐시 키 생성
     * 
     * @param orderId 주문 ID
     * @return 주문 상세 캐시 키 (예: order:info:order_1)
     */
    public String generateOrderCacheKey(Long orderId) {
        return String.join(SEPARATOR, ORDER_DOMAIN, INFO_TYPE, "order_" + orderId);
    }
    
    /**
     * 사용자 쿠폰 목록 캐시 키 생성
     * 
     * @param userId 사용자 ID
     * @param limit 조회 개수
     * @param offset 오프셋
     * @return 쿠폰 목록 캐시 키 (예: coupon:list:user_1_limit_10_offset_0)
     */
    public String generateCouponListCacheKey(Long userId, int limit, int offset) {
        return String.join(SEPARATOR, COUPON_DOMAIN, LIST_TYPE, 
            "user_" + userId + "_limit_" + limit + "_offset_" + offset);
    }
    
    /**
     * 쿠폰 히스토리 캐시 키 생성
     * 
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (선택적)
     * @return 쿠폰 히스토리 캐시 키 (예: coupon:history:user_1 또는 coupon:history:user_1_coupon_2)
     */
    public String generateCouponHistoryCacheKey(Long userId, Long couponId) {
        if (couponId != null) {
            return String.join(SEPARATOR, COUPON_DOMAIN, HISTORY_TYPE, 
                "user_" + userId + "_coupon_" + couponId);
        }
        return String.join(SEPARATOR, COUPON_DOMAIN, HISTORY_TYPE, "user_" + userId);
    }
    
    /**
     * 상품 통계 캐시 키 생성 (인기도, 판매량 등)
     * 
     * @param productId 상품 ID
     * @return 상품 통계 캐시 키 (예: product:stats:product_1)
     */
    public String generateProductStatsCacheKey(Long productId) {
        return String.join(SEPARATOR, PRODUCT_DOMAIN, STATS_TYPE, "product_" + productId);
    }
    
    /**
     * 커스텀 캐시 키 생성
     * 특별한 캐시 요구사항이 있을 때 사용
     * 
     * @param domain 도메인
     * @param type 캐시 타입
     * @param identifier 식별자
     * @return 커스텀 캐시 키
     */
    public String generateCustomCacheKey(String domain, String type, String identifier) {
        return String.join(SEPARATOR, domain, type, identifier);
    }
    
    /**
     * 키에서 캐시 타입 추출
     * 
     * @param cacheKey 캐시 키
     * @return 캐시 타입 (3단계 구조에서 중간 부분)
     */
    public String extractCacheType(String cacheKey) {
        if (cacheKey == null || !cacheKey.contains(SEPARATOR)) {
            return null;
        }
        String[] parts = cacheKey.split(SEPARATOR);
        return parts.length >= 3 ? parts[1] : null;
    }
    
    /**
     * 캐시 키 유효성 검증
     * 
     * @param cacheKey 캐시 키
     * @return 유효성 여부
     */
    public boolean isValidCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = cacheKey.split(SEPARATOR);
        return parts.length >= 3; // domain:type:identifier 최소 구조 (캐시 키는 3단계)
    }
}