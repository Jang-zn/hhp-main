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
        public static final String INVALID_USER_ID = "사용자 ID는 필수입니다";
        public static final String INVALID_USER_ID_POSITIVE = "사용자 ID는 양수여야 합니다";
        public static final String INVALID_AMOUNT_REQUIRED = "충전 금액은 필수입니다";
        public static final String INVALID_AMOUNT_POSITIVE = "충전 금액은 0보다 커야 합니다";
        public static final String BALANCE_NOT_FOUND = "Balance not found";
        public static final String INSUFFICIENT_BALANCE = "Insufficient balance";
        public static final String CONCURRENCY_CONFLICT = "Balance concurrency conflict";
        public static final String INVALID_USER = "Invalid user for balance operation";
        public static final String INVALID_AMOUNT = "Invalid amount for balance operation";
        
        // Repository 레벨 validation 메시지들
        public static final String BALANCE_CANNOT_BE_NULL = "Balance cannot be null";
        public static final String BALANCE_USER_CANNOT_BE_NULL = "Balance user cannot be null";
        public static final String USER_CANNOT_BE_NULL = "User cannot be null";
        public static final String USER_ID_CANNOT_BE_NULL = "User ID cannot be null";
    }
    
    // 잔액 관련 예외들
    public static class NotFound extends BalanceException {
        public NotFound() {
            super("ERR_BALANCE_NOT_FOUND", Messages.BALANCE_NOT_FOUND);
        }
    }
    
    public static class InsufficientBalance extends BalanceException {
        public InsufficientBalance() {
            super("ERR_BALANCE_INSUFFICIENT", Messages.INSUFFICIENT_BALANCE);
        }
    }
    
    public static class ConcurrencyConflict extends BalanceException {
        public ConcurrencyConflict() {
            super("ERR_BALANCE_CONCURRENCY_CONFLICT", Messages.CONCURRENCY_CONFLICT);
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
}