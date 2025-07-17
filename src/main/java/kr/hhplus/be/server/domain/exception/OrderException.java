package kr.hhplus.be.server.domain.exception;

public class OrderException extends RuntimeException {
    private final String errorCode;
    
    public OrderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 주문 관련 예외들
    public static class InvalidUser extends OrderException {
        public InvalidUser() {
            super("ERR_ORDER_INVALID_USER", "Invalid user ID");
        }
    }
    
    public static class OutOfStock extends OrderException {
        public OutOfStock() {
            super("ERR_ORDER_OUT_OF_STOCK", "One or more products out of stock");
        }
    }
    
    public static class ConcurrencyConflict extends OrderException {
        public ConcurrencyConflict() {
            super("ERR_ORDER_CONCURRENCY_CONFLICT", "Concurrent order creation conflict");
        }
    }
    
    public static class NotFound extends OrderException {
        public NotFound() {
            super("ERR_ORDER_NOT_FOUND", "Order not found");
        }
    }
    
    public static class Unauthorized extends OrderException {
        public Unauthorized() {
            super("ERR_ORDER_UNAUTHORIZED", "Unauthorized access to order");
        }
    }
} 