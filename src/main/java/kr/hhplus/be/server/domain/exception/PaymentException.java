package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

public class PaymentException extends RuntimeException {
    private final String errorCode;
    
    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    
    // 결제 관련 예외들
    
    public static class FailedToProcessPayment extends PaymentException {
        public FailedToProcessPayment() {
            super(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
    }

    public static class PaymentIdCannotBeNull extends PaymentException {
        public PaymentIdCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class PaymentCannotBeNull extends PaymentException {
        public PaymentCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class PaymentAmountCannotBeNegative extends PaymentException {
        public PaymentAmountCannotBeNegative() {
            super(ErrorCode.NEGATIVE_AMOUNT.getCode(), ErrorCode.NEGATIVE_AMOUNT.getMessage());
        }
    }

    public static class PaymentStatusCannotBeNull extends PaymentException {
        public PaymentStatusCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }
} 