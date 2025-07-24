package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

public class BalanceException extends RuntimeException {
    private final String errorCode;
    
    public BalanceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    
    // 잔액 관련 예외들
    public static class NotFound extends BalanceException {
        public NotFound() {
            super(ErrorCode.BALANCE_NOT_FOUND.getCode(), ErrorCode.BALANCE_NOT_FOUND.getMessage());
        }
    }
    
    public static class InvalidAmount extends BalanceException {
        public InvalidAmount() {
            super(ErrorCode.INVALID_AMOUNT.getCode(), ErrorCode.INVALID_AMOUNT.getMessage());
        }
    }
    
    public static class InsufficientBalance extends BalanceException {
        public InsufficientBalance() {
            super(ErrorCode.INSUFFICIENT_BALANCE.getCode(), ErrorCode.INSUFFICIENT_BALANCE.getMessage());
        }
    }

    public static class InvalidAmountRequired extends BalanceException {
        public InvalidAmountRequired() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class InvalidAmountPositive extends BalanceException {
        public InvalidAmountPositive() {
            super(ErrorCode.NEGATIVE_AMOUNT.getCode(), ErrorCode.NEGATIVE_AMOUNT.getMessage());
        }
    }

    public static class AmountCannotBeNull extends BalanceException {
        public AmountCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class FailedToRetrieveBalance extends BalanceException {
        public FailedToRetrieveBalance() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToChargeBalance extends BalanceException {
        public FailedToChargeBalance() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class UserIdAndAmountRequired extends BalanceException {
        public UserIdAndAmountRequired() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class BalanceCannotBeNull extends BalanceException {
        public BalanceCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }
}