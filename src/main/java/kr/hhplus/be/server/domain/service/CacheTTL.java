package kr.hhplus.be.server.domain.service;

/**
 * 모든 TTL 값은 초(seconds) 단위입니다.
 */
public enum CacheTTL {
    
    // === 상품 관련 캐시 ===
    
    /**
     * 단일 상품 정보 - 1시간
     * 상품 정보는 자주 변경되지 않으므로 상대적으로 긴 TTL 설정
     */
    PRODUCT_DETAIL(3600),
    
    /**
     * 상품 목록 - 1시간  
     * 상품 목록도 자주 변경되지 않으므로 긴 TTL 설정
     */
    PRODUCT_LIST(3600),
    
    // === 주문 관련 캐시 ===
    
    /**
     * 주문 상세 정보 - 10분
     * 주문 상태 변경(결제, 배송 등) 가능성을 고려한 중간 길이 TTL
     */
    ORDER_DETAIL(600),
    
    /**
     * 주문 목록 - 5분
     * 새로운 주문 생성 빈도를 고려한 짧은 TTL
     */
    ORDER_LIST(300),
    
    // === 잔액 관련 캐시 ===
    
    /**
     * 사용자 잔액 - 1분, 짧은 TTL
     */
    USER_BALANCE(60),
    
    // === 쿠폰 관련 캐시 ===
    
    /**
     * 사용자 쿠폰 목록 - 5분, 중간 길이 TTL
     */
    USER_COUPON_LIST(300);
    
    private final int seconds;
    
    CacheTTL(int seconds) {
        this.seconds = seconds;
    }
    
    /**
     * TTL 값을 초 단위로 반환
     * @return TTL 값 (초)
     */
    public int getSeconds() {
        return seconds;
    }
    
    /**
     * TTL 값을 분 단위로 반환
     * @return TTL 값 (분)
     */
    public int getMinutes() {
        return seconds / 60;
    }
    
    /**
     * 조회 기간에 따라 적절한 TTL을 동적으로 계산.
     * 
     * @param period 조회 기간 (일)
     * @return 적절한 TTL 값 (초)
     */
    public static int getPopularProductTTLSeconds(int period) {
        if (period <= 1) {
            return 300;  // 5분 - 실시간성 중요
        } else if (period <= 3) {
            return 600;  // 10분 - 단기 트렌드
        } else if (period <= 7) {
            return 1800; // 30분 - 주간 트렌드
        } else if (period <= 30) {
            return 3600; // 1시간 - 월간 트렌드
        } else {
            return 7200; // 2시간 - 장기 트렌드
        }
    }
 
    /**
     * TTL 정보를 문자열로 출력 (디버깅/로깅용)
     * @return TTL 정보 문자열
     */
    @Override
    public String toString() {
        return String.format("%s(%d초, %d분)", name(), seconds, getMinutes());
    }
}