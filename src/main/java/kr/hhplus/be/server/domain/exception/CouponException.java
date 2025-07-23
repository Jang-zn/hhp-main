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
        // UseCase 메시지들
        public static final String FAILED_TO_RETRIEVE_COUPON_LIST = "쿠폰 목록을 가져오는데 실패했습니다";
        public static final String COUPON_NOT_YET_STARTED = "쿠폰이 아직 시작되지 않았습니다";
        public static final String COUPON_STOCK_EXCEEDED = "쿠폰 재고를 초과했습니다";
        
        // 비즈니스 로직 메시지들
        public static final String COUPON_NOT_FOUND = "쿠폰을 찾을 수 없습니다";
        public static final String COUPON_EXPIRED = "쿠폰이 만료되었습니다";
        public static final String COUPON_OUT_OF_STOCK = "쿠폰 재고가 소진되었습니다";
        public static final String COUPON_ALREADY_ISSUED = "이미 발급받은 쿠폰입니다";
        public static final String INVALID_COUPON_ID_POSITIVE = "쿠폰 ID는 양수여야 합니다";
        public static final String COUPON_ID_CANNOT_BE_NULL = "쿠폰 ID는 null일 수 없습니다";
        public static final String USERID_AND_COUPONID_REQUIRED = "사용자 ID와 쿠폰 ID가 필요합니다";
        
        // 상태 관련 메시지들
        public static final String INVALID_STATUS_TRANSITION = "유효하지 않은 상태 전환입니다";
        public static final String INVALID_HISTORY_STATUS_TRANSITION = "유효하지 않은 쿠폰 히스토리 상태 전환입니다";
        public static final String COUPON_NOT_USABLE = "사용할 수 없는 쿠폰입니다";
        public static final String COUPON_NOT_ISSUABLE = "발급할 수 없는 상태의 쿠폰입니다";
        public static final String COUPON_DATA_INVALID = "유효하지 않은 쿠폰 데이터입니다";
        public static final String USER_DATA_INVALID = "유효하지 않은 사용자 데이터입니다";
        public static final String COUPON_HISTORY_DATA_INVALID = "유효하지 않은 쿠폰 히스토리 데이터입니다";
        public static final String INVALID_PAGINATION_PARAMS = "유효하지 않은 페이지네이션 파라미터입니다";
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

    public static class FailedToRetrieveCouponList extends CouponException {
        public FailedToRetrieveCouponList() {
            super("ERR_COUPON_FAILED_TO_RETRIEVE_LIST", Messages.FAILED_TO_RETRIEVE_COUPON_LIST);
        }
    }

    public static class CouponNotYetStarted extends CouponException {
        public CouponNotYetStarted() {
            super("ERR_COUPON_NOT_YET_STARTED", Messages.COUPON_NOT_YET_STARTED);
        }
    }

    public static class CouponStockExceeded extends CouponException {
        public CouponStockExceeded() {
            super("ERR_COUPON_STOCK_EXCEEDED", Messages.COUPON_STOCK_EXCEEDED);
        }
    }

    public static class InvalidCouponIdPositive extends CouponException {
        public InvalidCouponIdPositive() {
            super("ERR_COUPON_INVALID_ID_POSITIVE", Messages.INVALID_COUPON_ID_POSITIVE);
        }
    }

    public static class CouponIdCannotBeNull extends CouponException {
        public CouponIdCannotBeNull() {
            super("ERR_COUPON_ID_CANNOT_BE_NULL", Messages.COUPON_ID_CANNOT_BE_NULL);
        }
    }

    public static class UserIdAndCouponIdRequired extends CouponException {
        public UserIdAndCouponIdRequired() {
            super("ERR_COUPON_USERID_AND_COUPONID_REQUIRED", Messages.USERID_AND_COUPONID_REQUIRED);
        }
    }

    public static class InvalidStatusTransition extends CouponException {
        public InvalidStatusTransition(String message) {
            super("ERR_COUPON_INVALID_STATUS_TRANSITION", Messages.INVALID_STATUS_TRANSITION + ": " + message);
        }
    }

    public static class InvalidHistoryStatusTransition extends CouponException {
        public InvalidHistoryStatusTransition(String message) {
            super("ERR_COUPON_INVALID_HISTORY_STATUS_TRANSITION", Messages.INVALID_HISTORY_STATUS_TRANSITION + ": " + message);
        }
    }

    public static class CouponNotUsable extends CouponException {
        public CouponNotUsable() {
            super("ERR_COUPON_NOT_USABLE", Messages.COUPON_NOT_USABLE);
        }
    }

    public static class CouponNotIssuable extends CouponException {
        public CouponNotIssuable() {
            super("ERR_COUPON_NOT_ISSUABLE", Messages.COUPON_NOT_ISSUABLE);
        }
    }

    public static class InvalidCouponData extends CouponException {
        public InvalidCouponData(String message) {
            super("ERR_COUPON_INVALID_DATA", Messages.COUPON_DATA_INVALID + ": " + message);
        }
    }

    public static class InvalidUserData extends CouponException {
        public InvalidUserData(String message) {
            super("ERR_COUPON_INVALID_USER_DATA", Messages.USER_DATA_INVALID + ": " + message);
        }
    }

    public static class InvalidCouponHistoryData extends CouponException {
        public InvalidCouponHistoryData(String message) {
            super("ERR_COUPON_INVALID_HISTORY_DATA", Messages.COUPON_HISTORY_DATA_INVALID + ": " + message);
        }
    }

    public static class InvalidPaginationParams extends CouponException {
        public InvalidPaginationParams(String message) {
            super("ERR_COUPON_INVALID_PAGINATION", Messages.INVALID_PAGINATION_PARAMS + ": " + message);
        }
    }
} 