package kr.hhplus.be.server.domain.exception;

public class CouponException extends RuntimeException {
    private final String errorCode;
    
    public CouponException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 쿠폰 관련 예외들
    public static class NotFound extends CouponException {
        public NotFound() {
            super("ERR_COUPON_NOT_FOUND", "Coupon not found");
        }
    }
    
    public static class Expired extends CouponException {
        public Expired() {
            super("ERR_COUPON_EXPIRED", "Coupon has expired");
        }
    }
    
    public static class OutOfStock extends CouponException {
        public OutOfStock() {
            super("ERR_COUPON_OUT_OF_STOCK", "Coupon stock exhausted");
        }
    }
    
    public static class AlreadyIssued extends CouponException {
        public AlreadyIssued() {
            super("ERR_COUPON_ALREADY_ISSUED", "Coupon already issued by user");
        }
    }
} 