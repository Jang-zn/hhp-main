package kr.hhplus.be.server.domain.exception;

public class PaymentException extends RuntimeException {
    private final String errorCode;
    
    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들
        public static final String FAILED_TO_PROCESS_PAYMENT = "결제 처리에 실패했습니다";
        
        // Repository 레벨 validation 메시지들
        public static final String PAYMENT_CANNOT_BE_NULL = "결제는 null일 수 없습니다";
        public static final String PAYMENT_ORDER_CANNOT_BE_NULL = "결제 주문은 null일 수 없습니다";
        public static final String PAYMENT_AMOUNT_CANNOT_BE_NEGATIVE = "결제 금액은 음수일 수 없습니다";
        public static final String PAYMENT_STATUS_CANNOT_BE_NULL = "결제 상태는 null일 수 없습니다";
    }
    
    // 결제 관련 예외들
    
    public static class FailedToProcessPayment extends PaymentException {
        public FailedToProcessPayment() {
            super("ERR_PAYMENT_FAILED_TO_PROCESS", Messages.FAILED_TO_PROCESS_PAYMENT);
        }
    }

    public static class PaymentCannotBeNull extends PaymentException {
        public PaymentCannotBeNull() {
            super("ERR_PAYMENT_CANNOT_BE_NULL", Messages.PAYMENT_CANNOT_BE_NULL);
        }
    }

    public static class PaymentOrderCannotBeNull extends PaymentException {
        public PaymentOrderCannotBeNull() {
            super("ERR_PAYMENT_ORDER_CANNOT_BE_NULL", Messages.PAYMENT_ORDER_CANNOT_BE_NULL);
        }
    }

    public static class PaymentAmountCannotBeNegative extends PaymentException {
        public PaymentAmountCannotBeNegative() {
            super("ERR_PAYMENT_AMOUNT_CANNOT_BE_NEGATIVE", Messages.PAYMENT_AMOUNT_CANNOT_BE_NEGATIVE);
        }
    }

    public static class PaymentStatusCannotBeNull extends PaymentException {
        public PaymentStatusCannotBeNull() {
            super("ERR_PAYMENT_STATUS_CANNOT_BE_NULL", Messages.PAYMENT_STATUS_CANNOT_BE_NULL);
        }
    }
} 