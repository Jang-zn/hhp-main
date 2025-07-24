package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

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
            super(ErrorCode.COUPON_NOT_FOUND.getCode(), ErrorCode.COUPON_NOT_FOUND.getMessage());
        }
    }
    
    public static class Expired extends CouponException {
        public Expired() {
            super(ErrorCode.COUPON_EXPIRED.getCode(), ErrorCode.COUPON_EXPIRED.getMessage());
        }
    }
    
    public static class OutOfStock extends CouponException {
        public OutOfStock() {
            super(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getCode(), ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getMessage());
        }
    }
    
    public static class AlreadyIssued extends CouponException {
        public AlreadyIssued() {
            super(ErrorCode.COUPON_ALREADY_ISSUED.getCode(), ErrorCode.COUPON_ALREADY_ISSUED.getMessage());
        }
    }

    public static class FailedToRetrieveCouponList extends CouponException {
        public FailedToRetrieveCouponList() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class CouponNotYetStarted extends CouponException {
        public CouponNotYetStarted() {
            super(ErrorCode.COUPON_NOT_YET_STARTED.getCode(), ErrorCode.COUPON_NOT_YET_STARTED.getMessage());
        }
    }

    public static class CouponStockExceeded extends CouponException {
        public CouponStockExceeded() {
            super(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getCode(), ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getMessage());
        }
    }

    public static class InvalidCouponIdPositive extends CouponException {
        public InvalidCouponIdPositive() {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage());
        }
    }

    public static class CouponIdCannotBeNull extends CouponException {
        public CouponIdCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class UserIdAndCouponIdRequired extends CouponException {
        public UserIdAndCouponIdRequired() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class InvalidStatusTransition extends CouponException {
        public InvalidStatusTransition(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }

    public static class InvalidHistoryStatusTransition extends CouponException {
        public InvalidHistoryStatusTransition(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }

    public static class CouponNotUsable extends CouponException {
        public CouponNotUsable() {
            super(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    public static class CouponNotIssuable extends CouponException {
        public CouponNotIssuable() {
            super(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    public static class InvalidCouponData extends CouponException {
        public InvalidCouponData(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }

    public static class InvalidUserData extends CouponException {
        public InvalidUserData(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }

    public static class InvalidCouponHistoryData extends CouponException {
        public InvalidCouponHistoryData(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }

    public static class InvalidPaginationParams extends CouponException {
        public InvalidPaginationParams(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage() + ": " + message);
        }
    }
} 