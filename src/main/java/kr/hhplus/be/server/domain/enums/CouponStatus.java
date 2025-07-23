package kr.hhplus.be.server.domain.enums;

/**
 * 쿠폰 상태를 나타내는 열거형
 * 쿠폰의 생명주기를 명확하게 정의하고 상태 전이를 관리합니다.
 */
public enum CouponStatus {
    /**
     * 활성화 대기 상태
     * - 쿠폰이 생성되었지만 아직 발급 시작 시간이 되지 않은 상태
     * - startDate > now
     */
    INACTIVE("활성화 대기", "쿠폰 발급이 아직 시작되지 않았습니다"),
    
    /**
     * 발급 가능 상태
     * - 발급 시작 시간이 되었고, 재고가 남아있으며, 만료되지 않은 상태
     * - startDate <= now <= endDate AND issuedCount < maxIssuance
     */
    ACTIVE("발급 가능", "쿠폰 발급이 가능합니다"),
    
    /**
     * 재고 소진 상태
     * - 최대 발급 수량에 도달한 상태
     * - issuedCount >= maxIssuance
     */
    SOLD_OUT("재고 소진", "쿠폰 재고가 모두 소진되었습니다"),
    
    /**
     * 만료 상태
     * - 쿠폰 유효 기간이 지난 상태
     * - now > endDate
     */
    EXPIRED("만료", "쿠폰이 만료되었습니다"),
    
    /**
     * 비활성 상태
     * - 관리자에 의해 강제로 비활성화된 상태
     * - 긴급하게 쿠폰 발급을 중단해야 할 때 사용
     */
    DISABLED("비활성", "쿠폰이 비활성화되었습니다");
    
    private final String description;
    private final String message;
    
    CouponStatus(String description, String message) {
        this.description = description;
        this.message = message;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * 발급 가능한 상태인지 확인
     */
    public boolean isIssuable() {
        return this == ACTIVE;
    }
    
    /**
     * 상태 전이 가능성 검증
     */
    public boolean canTransitionTo(CouponStatus newStatus) {
        switch (this) {
            case INACTIVE:
                return newStatus == ACTIVE || newStatus == EXPIRED || newStatus == DISABLED;
            case ACTIVE:
                return newStatus == SOLD_OUT || newStatus == EXPIRED || newStatus == DISABLED;
            case SOLD_OUT:
                return newStatus == EXPIRED || newStatus == DISABLED;
            case EXPIRED:
                return newStatus == DISABLED; // 만료된 쿠폰은 비활성화만 가능
            case DISABLED:
                return newStatus == ACTIVE; // 비활성화된 쿠폰은 다시 활성화 가능
            default:
                return false;
        }
    }
}