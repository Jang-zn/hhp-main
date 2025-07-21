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
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들
        public static final String PRODUCT_ID_CANNOT_BE_NULL = "상품 ID는 필수입니다";
        public static final String PRODUCT_ID_CANNOT_BE_NEGATIVE = "상품 ID는 양수여야 합니다";
        public static final String PRODUCT_QUANTITY_CANNOT_BE_NULL = "상품 수량은 필수입니다";
        public static final String PRODUCT_QUANTITY_CANNOT_BE_NEGATIVE = "상품 수량은 0보다 커야 합니다";
        public static final String INVALID_DAYS_POSITIVE = "조회 기간(일)은 양수여야 합니다";
        public static final String INVALID_DAYS_MAX = "조회 기간은 30일 이하여야 합니다";
        
        // UseCase 메시지들
        public static final String PRODUCT_ID_CANNOT_BE_NULL_USECASE = "상품 ID는 null일 수 없습니다";
        public static final String DAYS_MUST_BE_POSITIVE = "조회 기간은 0보다 커야 합니다";
        public static final String DAYS_CANNOT_EXCEED_MAX = "조회 기간은 30일을 초과할 수 없습니다";
        public static final String FAILED_TO_RETRIEVE_PRODUCT = "상품 조회에 실패했습니다";
        public static final String FAILED_TO_RETRIEVE_POPULAR_PRODUCTS = "인기 상품 조회에 실패했습니다";
        
        // Controller 메시지들
        public static final String PRODUCTID_CANNOT_BE_NULL = "상품 ID는 null일 수 없습니다";
        
        // 비즈니스 로직 메시지들
        public static final String PRODUCT_NOT_FOUND = "상품을 찾을 수 없습니다";
        public static final String OUT_OF_STOCK = "상품이 품절되었습니다";
        public static final String INVALID_RESERVATION = "잘못된 예약입니다";
        
        // Repository 레벨 validation 메시지들
        public static final String PRODUCT_CANNOT_BE_NULL = "상품은 null일 수 없습니다";
        public static final String PRODUCT_ID_CANNOT_BE_NULL_REPO = "상품 ID는 null일 수 없습니다";
        public static final String PRODUCT_NAME_CANNOT_BE_NULL = "상품명은 null일 수 없습니다";
        public static final String PRODUCT_PRICE_CANNOT_BE_NEGATIVE = "상품 가격은 음수일 수 없습니다";
        public static final String PRODUCT_STOCK_CANNOT_BE_NEGATIVE = "상품 재고는 음수일 수 없습니다";
    }
    
    // 상품 관련 예외들
    public static class NotFound extends ProductException {
        public NotFound() {
            super("ERR_PRODUCT_NOT_FOUND", Messages.PRODUCT_NOT_FOUND);
        }
    }
    
    public static class OutOfStock extends ProductException {
        public OutOfStock() {
            super("ERR_PRODUCT_OUT_OF_STOCK", Messages.OUT_OF_STOCK);
        }
    }
    
    public static class InvalidReservation extends ProductException {
        public InvalidReservation(String message) {
            super("ERR_PRODUCT_INVALID_RESERVATION", message);
        }
    }

    public static class InvalidProductId extends ProductException {
        public InvalidProductId() {
            super("ERR_PRODUCT_INVALID_PRODUCT_ID", Messages.PRODUCT_ID_CANNOT_BE_NULL);
        }
    }

    public static class InvalidProductIdNegative extends ProductException {
        public InvalidProductIdNegative() {
            super("ERR_PRODUCT_INVALID_PRODUCT_ID_NEGATIVE", Messages.PRODUCT_ID_CANNOT_BE_NEGATIVE);
        }
    }

    public static class InvalidProductQuantity extends ProductException {
        public InvalidProductQuantity() {
            super("ERR_PRODUCT_INVALID_PRODUCT_QUANTITY", Messages.PRODUCT_QUANTITY_CANNOT_BE_NULL);
        }
    }

    public static class InvalidProductQuantityNegative extends ProductException {
        public InvalidProductQuantityNegative() {
            super("ERR_PRODUCT_INVALID_PRODUCT_QUANTITY_NEGATIVE", Messages.PRODUCT_QUANTITY_CANNOT_BE_NEGATIVE);
        }
    }

    public static class InvalidDaysPositive extends ProductException {
        public InvalidDaysPositive() {
            super("ERR_PRODUCT_INVALID_DAYS_POSITIVE", Messages.INVALID_DAYS_POSITIVE);
        }
    }

    public static class InvalidDaysMax extends ProductException {
        public InvalidDaysMax() {
            super("ERR_PRODUCT_INVALID_DAYS_MAX", Messages.INVALID_DAYS_MAX);
        }
    }

    public static class FailedToRetrieveProduct extends ProductException {
        public FailedToRetrieveProduct() {
            super("ERR_PRODUCT_FAILED_TO_RETRIEVE", Messages.FAILED_TO_RETRIEVE_PRODUCT);
        }
    }

    public static class FailedToRetrievePopularProducts extends ProductException {
        public FailedToRetrievePopularProducts() {
            super("ERR_PRODUCT_FAILED_TO_RETRIEVE_POPULAR", Messages.FAILED_TO_RETRIEVE_POPULAR_PRODUCTS);
        }
    }

    public static class ProductCannotBeNull extends ProductException {
        public ProductCannotBeNull() {
            super("ERR_PRODUCT_CANNOT_BE_NULL", Messages.PRODUCT_CANNOT_BE_NULL);
        }
    }

    public static class ProductNameCannotBeNull extends ProductException {
        public ProductNameCannotBeNull() {
            super("ERR_PRODUCT_NAME_CANNOT_BE_NULL", Messages.PRODUCT_NAME_CANNOT_BE_NULL);
        }
    }

    public static class ProductPriceCannotBeNegative extends ProductException {
        public ProductPriceCannotBeNegative() {
            super("ERR_PRODUCT_PRICE_CANNOT_BE_NEGATIVE", Messages.PRODUCT_PRICE_CANNOT_BE_NEGATIVE);
        }
    }

    public static class ProductStockCannotBeNegative extends ProductException {
        public ProductStockCannotBeNegative() {
            super("ERR_PRODUCT_STOCK_CANNOT_BE_NEGATIVE", Messages.PRODUCT_STOCK_CANNOT_BE_NEGATIVE);
        }
    } 