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
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들
        public static final String INVALID_USER_ID = "사용자 ID는 필수입니다";
        public static final String INVALID_USER_ID_POSITIVE = "사용자 ID는 양수여야 합니다";
        public static final String INVALID_COUPON_ID_POSITIVE = "쿠폰 ID는 양수여야 합니다";
        public static final String INVALID_LIMIT_POSITIVE = "limit은 양수여야 합니다";
        public static final String INVALID_LIMIT_MAX = "limit은 100 이하여야 합니다";
        public static final String INVALID_OFFSET = "offset은 0 이상이어야 합니다";
        
        // UseCase 메시지들
        public static final String USER_ID_CANNOT_BE_NULL = "User ID cannot be null";
        public static final String COUPON_ID_CANNOT_BE_NULL = "Coupon ID cannot be null";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String LIMIT_MUST_BE_POSITIVE = "Limit must be greater than 0";
        public static final String LIMIT_CANNOT_EXCEED_MAX = "Limit cannot exceed 1000";
        public static final String OFFSET_MUST_BE_NON_NEGATIVE = "Offset must be non-negative";
        public static final String FAILED_TO_RETRIEVE_COUPON_LIST = "Failed to retrieve coupon list";
        public static final String COUPON_NOT_YET_STARTED = "Coupon not yet started";
        public static final String COUPON_STOCK_EXCEEDED = "Coupon stock exceeded";
        
        // Controller 메시지들
        public static final String REQUEST_CANNOT_BE_NULL = "Request cannot be null";
        public static final String USERID_AND_COUPONID_REQUIRED = "UserId and CouponId are required";
        public static final String USERID_CANNOT_BE_NULL = "UserId cannot be null";
        public static final String INVALID_PAGINATION_PARAMETERS = "Invalid pagination parameters";
        
        // 비즈니스 로직 메시지들
        public static final String COUPON_NOT_FOUND = "Coupon not found";
        public static final String COUPON_EXPIRED = "Coupon has expired";
        public static final String COUPON_OUT_OF_STOCK = "Coupon stock exhausted";
        public static final String COUPON_ALREADY_ISSUED = "Coupon already issued by user";
        public static final String COUPON_CONCURRENCY_CONFLICT = "Coupon concurrency conflict";
    }
    
    // 쿠폰 관련 예외들
    public static class NotFound extends CouponException {
        public NotFound() {
            super("ERR_COUPON_NOT_FOUND", Messages.COUPON_NOT_FOUND);
        }
    }
    
    public static class Expired extends CouponException {
        public Expired() {
            super("ERR_COUPON_EXPIRED", Messages.COUPON_EXPIRED);
        }
    }
    
    public static class OutOfStock extends CouponException {
        public OutOfStock() {
            super("ERR_COUPON_OUT_OF_STOCK", Messages.COUPON_OUT_OF_STOCK);
        }
    }
    
    public static class AlreadyIssued extends CouponException {
        public AlreadyIssued() {
            super("ERR_COUPON_ALREADY_ISSUED", Messages.COUPON_ALREADY_ISSUED);
        }
    }
    
    public static class ConcurrencyConflict extends CouponException {
        public ConcurrencyConflict() {
            super("ERR_COUPON_CONCURRENCY_CONFLICT", Messages.COUPON_CONCURRENCY_CONFLICT);
        }
    }
} 