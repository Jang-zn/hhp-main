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
    
    // 잔액 관련 예외들
    public static class NotFound extends BalanceException {
        public NotFound() {
            super("ERR_BALANCE_NOT_FOUND", "Balance not found");
        }
    }
    
    public static class InsufficientBalance extends BalanceException {
        public InsufficientBalance() {
            super("ERR_BALANCE_INSUFFICIENT", "Insufficient balance");
        }
    }
    
    public static class ConcurrencyConflict extends BalanceException {
        public ConcurrencyConflict() {
            super("ERR_BALANCE_CONCURRENCY_CONFLICT", "Balance concurrency conflict");
        }
    }
    
    public static class InvalidUser extends BalanceException {
        public InvalidUser() {
            super("ERR_BALANCE_INVALID_USER", "Invalid user for balance operation");
        }
    }
    
    public static class InvalidAmount extends BalanceException {
        public InvalidAmount() {
            super("ERR_BALANCE_INVALID_AMOUNT", "Invalid amount for balance operation");
        }
    }
}