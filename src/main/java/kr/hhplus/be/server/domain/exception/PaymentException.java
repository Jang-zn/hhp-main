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
    
    // 결제 관련 예외들
    public static class InsufficientBalance extends PaymentException {
        public InsufficientBalance() {
            super("ERR_PAYMENT_INSUFFICIENT_BALANCE", "Insufficient balance");
        }
    }
    
    public static class InvalidCoupon extends PaymentException {
        public InvalidCoupon() {
            super("ERR_PAYMENT_INVALID_COUPON", "Invalid coupon ID");
        }
    }
    
    public static class ConcurrencyConflict extends PaymentException {
        public ConcurrencyConflict() {
            super("ERR_PAYMENT_CONCURRENCY_CONFLICT", "Concurrent payment conflict");
        }
    }
    
    public static class OrderNotFound extends PaymentException {
        public OrderNotFound() {
            super("ERR_PAYMENT_ORDER_NOT_FOUND", "Order not found");
        }
    }
    
    public static class InvalidOrderStatus extends PaymentException {
        public InvalidOrderStatus() {
            super("ERR_PAYMENT_INVALID_ORDER_STATUS", "Order status not eligible for payment");
        }
    }
} 