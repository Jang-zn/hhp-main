package kr.hhplus.be.server.domain.exception;

public class ProductException extends RuntimeException {
    private final String errorCode;
    
    public ProductException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 상품 관련 예외들
    public static class NotFound extends ProductException {
        public NotFound() {
            super("ERR_PRODUCT_NOT_FOUND", "Product not found");
        }
    }
    
    public static class OutOfStock extends ProductException {
        public OutOfStock() {
            super("ERR_PRODUCT_OUT_OF_STOCK", "Product out of stock");
        }
    }
    
    public static class InvalidReservation extends ProductException {
        public InvalidReservation(String message) {
            super("ERR_PRODUCT_INVALID_RESERVATION", message);
        }
    }
} 