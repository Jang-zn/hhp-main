package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

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
    public static class NotFound extends OrderException {
        public NotFound() {
            super(ErrorCode.ORDER_NOT_FOUND.getCode(), ErrorCode.ORDER_NOT_FOUND.getMessage());
        }
    }
    public static class OrderIdCannotBeNull extends OrderException {
        public OrderIdCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }
    
    public static class Unauthorized extends OrderException {
        public Unauthorized() {
            super(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
        }
    }
    
    public static class EmptyItems extends OrderException {
        public EmptyItems() {
            super(ErrorCode.INVALID_ORDER_ITEMS.getCode(), ErrorCode.INVALID_ORDER_ITEMS.getMessage());
        }
    }
    
    public static class AlreadyPaid extends OrderException {
        public AlreadyPaid() {
            super(ErrorCode.ORDER_ALREADY_PAID.getCode(), ErrorCode.ORDER_ALREADY_PAID.getMessage());
        }
    }


    public static class ProductsCannotBeNull extends OrderException {
        public ProductsCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class FailedToCreateOrder extends OrderException {
        public FailedToCreateOrder() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToRetrieveOrder extends OrderException {
        public FailedToRetrieveOrder() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToRetrieveOrderList extends OrderException {
        public FailedToRetrieveOrderList() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToPayOrder extends OrderException {
        public FailedToPayOrder() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class UserIdAndProductIdsRequired extends OrderException {
        public UserIdAndProductIdsRequired() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class OrderCannotBeNull extends OrderException {
        public OrderCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }


    public static class OrderItemsCannotBeNull extends OrderException {
        public OrderItemsCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class OrderItemsCannotBeEmpty extends OrderException {
        public OrderItemsCannotBeEmpty() {
            super(ErrorCode.INVALID_ORDER_ITEMS.getCode(), ErrorCode.INVALID_ORDER_ITEMS.getMessage());
        }
    }
} 