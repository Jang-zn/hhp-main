package kr.hhplus.be.server.domain.enums;

/**
 * 개별 사용자 쿠폰 히스토리의 상태를 나타내는 열거형
 * 발급받은 쿠폰의 사용 이력을 추적합니다.
 */
public enum CouponHistoryStatus {
    /**
     * 발급됨 (사용 가능)
     * - 쿠폰이 발급되어 사용 가능한 상태
     */
    ISSUED("발급됨", "사용 가능한 쿠폰입니다"),
    
    /**
     * 사용됨
     * - 주문에서 쿠폰이 사용된 상태
     */
    USED("사용됨", "이미 사용된 쿠폰입니다"),
    
    /**
     * 만료됨
     * - 쿠폰 유효기간이 지나 사용할 수 없는 상태
     */
    EXPIRED("만료됨", "유효기간이 지난 쿠폰입니다");
    
    private final String description;
    private final String message;
    
    CouponHistoryStatus(String description, String message) {
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
     * 사용 가능한 상태인지 확인
     */
    public boolean isUsable() {
        return this == ISSUED;
    }
    
    /**
     * 상태 전이 가능성 검증
     */
    public boolean canTransitionTo(CouponHistoryStatus newStatus) {
        switch (this) {
            case ISSUED:
                return newStatus == USED || newStatus == EXPIRED;
            case USED:
                return false; // 사용된 쿠폰은 상태 변경 불가
            case EXPIRED:
                return false; // 만료된 쿠폰은 상태 변경 불가
            default:
                return false;
        }
    }
}