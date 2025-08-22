package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

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
            super(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
    }

    public static class OutOfStock extends ProductException {
        public OutOfStock() {
            super(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode(), ErrorCode.PRODUCT_OUT_OF_STOCK.getMessage());
        }
    }

    public static class InvalidReservation extends ProductException {
        public InvalidReservation(String message) {
            super(ErrorCode.INVALID_RESERVATION.getCode(), ErrorCode.INVALID_RESERVATION.getMessage() + ": " + message);
        }
    }

    public static class InvalidProductId extends ProductException {
        public InvalidProductId() {
            super(ErrorCode.INVALID_PRODUCT_ID.getCode(), ErrorCode.INVALID_PRODUCT_ID.getMessage());
        }
    }

    public static class InvalidProduct extends ProductException {
        public InvalidProduct(String message) {
            super(ErrorCode.INVALID_INPUT.getCode(), message);
        }
    }

    public static class InvalidProductIdNegative extends ProductException {
        public InvalidProductIdNegative() {
            super(ErrorCode.INVALID_PRODUCT_ID.getCode(), ErrorCode.INVALID_PRODUCT_ID.getMessage());
        }
    }

    public static class InvalidProductQuantity extends ProductException {
        public InvalidProductQuantity() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class InvalidProductQuantityNegative extends ProductException {
        public InvalidProductQuantityNegative() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class InvalidDaysPositive extends ProductException {
        public InvalidDaysPositive() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class InvalidDaysMax extends ProductException {
        public InvalidDaysMax() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class FailedToRetrieveProduct extends ProductException {
        public FailedToRetrieveProduct() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToRetrievePopularProducts extends ProductException {
        public FailedToRetrievePopularProducts() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class ProductCannotBeNull extends ProductException {
        public ProductCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class ProductNameCannotBeNull extends ProductException {
        public ProductNameCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class ProductPriceCannotBeNegative extends ProductException {
        public ProductPriceCannotBeNegative() {
            super(ErrorCode.NEGATIVE_AMOUNT.getCode(), ErrorCode.NEGATIVE_AMOUNT.getMessage());
        }
    }

    public static class ProductStockCannotBeNegative extends ProductException {
        public ProductStockCannotBeNegative() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }
}