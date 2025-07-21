package kr.hhplus.be.server.domain.exception;

public class BalanceException extends RuntimeException {
    private final String errorCode;
    
    public BalanceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들
        public static final String INVALID_AMOUNT_REQUIRED = "충전 금액은 필수입니다";
        public static final String INVALID_AMOUNT_POSITIVE = "충전 금액은 0보다 커야 합니다";
        
        // UseCase 메시지들
        public static final String AMOUNT_CANNOT_BE_NULL = "금액은 null일 수 없습니다";
        public static final String FAILED_TO_RETRIEVE_BALANCE = "잔액 정보를 가져오는데 실패했습니다";
        public static final String FAILED_TO_CHARGE_BALANCE = "잔액 충전에 실패했습니다";
        
        // Controller 메시지들
        public static final String USERID_AND_AMOUNT_REQUIRED = "사용자 ID와 금액이 필요합니다";
        
        // 비즈니스 로직 메시지들
        public static final String BALANCE_NOT_FOUND = "잔액 정보를 찾을 수 없습니다";
        public static final String INSUFFICIENT_BALANCE = "잔액이 부족합니다";
        public static final String INVALID_USER = "잔액 작업에 유효하지 않은 사용자입니다";
        public static final String INVALID_AMOUNT = "잔액 작업에 유효하지 않은 금액입니다";
        
        // Repository 레벨 validation 메시지들
        public static final String BALANCE_CANNOT_BE_NULL = "잔액은 null일 수 없습니다";
        public static final String BALANCE_USER_CANNOT_BE_NULL = "잔액 사용자는 null일 수 없습니다";
    }
    
    // 잔액 관련 예외들
    public static class NotFound extends BalanceException {
        public NotFound() {
            super("ERR_BALANCE_NOT_FOUND", Messages.BALANCE_NOT_FOUND);
        }
    }
    
    public static class InvalidUser extends BalanceException {
        public InvalidUser() {
            super("ERR_BALANCE_INVALID_USER", Messages.INVALID_USER);
        }
    }
    
    public static class InvalidAmount extends BalanceException {
        public InvalidAmount() {
            super("ERR_BALANCE_INVALID_AMOUNT", Messages.INVALID_AMOUNT);
        }
    }
    
    public static class InsufficientBalance extends BalanceException {
        public InsufficientBalance() {
            super("ERR_BALANCE_INSUFFICIENT", Messages.INSUFFICIENT_BALANCE);
        }
    }

    public static class InvalidAmountRequired extends BalanceException {
        public InvalidAmountRequired() {
            super("ERR_BALANCE_INVALID_AMOUNT_REQUIRED", Messages.INVALID_AMOUNT_REQUIRED);
        }
    }

    public static class InvalidAmountPositive extends BalanceException {
        public InvalidAmountPositive() {
            super("ERR_BALANCE_INVALID_AMOUNT_POSITIVE", Messages.INVALID_AMOUNT_POSITIVE);
        }
    }

    public static class AmountCannotBeNull extends BalanceException {
        public AmountCannotBeNull() {
            super("ERR_BALANCE_AMOUNT_CANNOT_BE_NULL", Messages.AMOUNT_CANNOT_BE_NULL);
        }
    }

    public static class FailedToRetrieveBalance extends BalanceException {
        public FailedToRetrieveBalance() {
            super("ERR_BALANCE_FAILED_TO_RETRIEVE", Messages.FAILED_TO_RETRIEVE_BALANCE);
        }
    }

    public static class FailedToChargeBalance extends BalanceException {
        public FailedToChargeBalance() {
            super("ERR_BALANCE_FAILED_TO_CHARGE", Messages.FAILED_TO_CHARGE_BALANCE);
        }
    }

    public static class UserIdAndAmountRequired extends BalanceException {
        public UserIdAndAmountRequired() {
            super("ERR_BALANCE_USERID_AMOUNT_REQUIRED", Messages.USERID_AND_AMOUNT_REQUIRED);
        }
    }

    public static class BalanceCannotBeNull extends BalanceException {
        public BalanceCannotBeNull() {
            super("ERR_BALANCE_CANNOT_BE_NULL", Messages.BALANCE_CANNOT_BE_NULL);
        }
    }

    public static class BalanceUserCannotBeNull extends BalanceException {
        public BalanceUserCannotBeNull() {
            super("ERR_BALANCE_USER_CANNOT_BE_NULL", Messages.BALANCE_USER_CANNOT_BE_NULL);
        }
    }
}