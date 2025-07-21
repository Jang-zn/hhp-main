package kr.hhplus.be.server.api;

public enum ApiMessage {
    // 잔액 관련
    BALANCE_CHARGED("잔액이 충전되었습니다."),
    BALANCE_RETRIEVED("잔액 조회 성공"),
    
    // 상품 관련
    PRODUCTS_RETRIEVED("상품 목록 조회 성공"),
    POPULAR_PRODUCTS_RETRIEVED("인기 상품 조회 성공"),
    
    // 주문 관련
    ORDER_CREATED("주문이 생성되었습니다."),
    PAYMENT_COMPLETED("결제가 완료되었습니다."),
    
    // 쿠폰 관련
    COUPON_ISSUED("쿠폰이 발급되었습니다."),
    COUPONS_RETRIEVED("쿠폰 목록 조회 성공"),
    
    // 공통
    SUCCESS("성공");
    
    private final String message;
    
    ApiMessage(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
} 