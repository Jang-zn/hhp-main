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
    
    // 메시지 상수들
    public static class Messages {
        
        // UseCase 메시지들
        public static final String PRODUCTS_CANNOT_BE_NULL = "상품 목록은 null일 수 없습니다";
        public static final String FAILED_TO_CREATE_ORDER = "주문 생성에 실패했습니다";
        public static final String FAILED_TO_RETRIEVE_ORDER = "주문 조회에 실패했습니다";
        public static final String FAILED_TO_RETRIEVE_ORDER_LIST = "주문 목록 조회에 실패했습니다";
        public static final String FAILED_TO_PAY_ORDER = "주문 결제에 실패했습니다";
        
        // Controller 메시지들
        public static final String USERID_AND_PRODUCTIDS_REQUIRED = "사용자 ID와 상품 ID 목록이 필요합니다";
        
        // 비즈니스 로직 메시지들
        public static final String ORDER_NOT_FOUND = "주문을 찾을 수 없습니다";
        public static final String UNAUTHORIZED_ACCESS = "주문에 대한 접근 권한이 없습니다";
        public static final String EMPTY_ITEMS = "주문에는 최소 하나의 상품이 포함되어야 합니다";
        public static final String ALREADY_PAID = "이미 결제된 주문입니다";
        
        // Repository 레벨 validation 메시지들
        public static final String ORDER_CANNOT_BE_NULL = "주문은 null일 수 없습니다";
        public static final String ORDER_ITEMS_CANNOT_BE_NULL = "주문 상품들은 null일 수 없습니다";
        public static final String ORDER_ITEMS_CANNOT_BE_EMPTY = "주문 상품들은 비어있을 수 없습니다";
    }
    
    // 주문 관련 예외들
    public static class NotFound extends OrderException {
        public NotFound() {
            super("ERR_ORDER_NOT_FOUND", Messages.ORDER_NOT_FOUND);
        }
    }
    
    public static class Unauthorized extends OrderException {
        public Unauthorized() {
            super("ERR_ORDER_UNAUTHORIZED", Messages.UNAUTHORIZED_ACCESS);
        }
    }
    
    public static class EmptyItems extends OrderException {
        public EmptyItems() {
            super("ERR_ORDER_EMPTY_ITEMS", Messages.EMPTY_ITEMS);
        }
    }
    
    public static class AlreadyPaid extends OrderException {
        public AlreadyPaid() {
            super("ERR_ORDER_ALREADY_PAID", Messages.ALREADY_PAID);
        }
    }


    public static class ProductsCannotBeNull extends OrderException {
        public ProductsCannotBeNull() {
            super("ERR_ORDER_PRODUCTS_CANNOT_BE_NULL", Messages.PRODUCTS_CANNOT_BE_NULL);
        }
    }

    public static class FailedToCreateOrder extends OrderException {
        public FailedToCreateOrder() {
            super("ERR_ORDER_FAILED_TO_CREATE", Messages.FAILED_TO_CREATE_ORDER);
        }
    }

    public static class FailedToRetrieveOrder extends OrderException {
        public FailedToRetrieveOrder() {
            super("ERR_ORDER_FAILED_TO_RETRIEVE", Messages.FAILED_TO_RETRIEVE_ORDER);
        }
    }

    public static class FailedToRetrieveOrderList extends OrderException {
        public FailedToRetrieveOrderList() {
            super("ERR_ORDER_FAILED_TO_RETRIEVE_LIST", Messages.FAILED_TO_RETRIEVE_ORDER_LIST);
        }
    }

    public static class FailedToPayOrder extends OrderException {
        public FailedToPayOrder() {
            super("ERR_ORDER_FAILED_TO_PAY", Messages.FAILED_TO_PAY_ORDER);
        }
    }

    public static class UserIdAndProductIdsRequired extends OrderException {
        public UserIdAndProductIdsRequired() {
            super("ERR_ORDER_USERID_AND_PRODUCTIDS_REQUIRED", Messages.USERID_AND_PRODUCTIDS_REQUIRED);
        }
    }

    public static class OrderCannotBeNull extends OrderException {
        public OrderCannotBeNull() {
            super("ERR_ORDER_CANNOT_BE_NULL", Messages.ORDER_CANNOT_BE_NULL);
        }
    }


    public static class OrderItemsCannotBeNull extends OrderException {
        public OrderItemsCannotBeNull() {
            super("ERR_ORDER_ITEMS_CANNOT_BE_NULL", Messages.ORDER_ITEMS_CANNOT_BE_NULL);
        }
    }

    public static class OrderItemsCannotBeEmpty extends OrderException {
        public OrderItemsCannotBeEmpty() {
            super("ERR_ORDER_ITEMS_CANNOT_BE_EMPTY", Messages.ORDER_ITEMS_CANNOT_BE_EMPTY);
        }
    }
} 