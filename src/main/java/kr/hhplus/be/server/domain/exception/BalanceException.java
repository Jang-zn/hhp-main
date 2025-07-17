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
    public static class InvalidUser extends BalanceException {
        public InvalidUser() {
            super("ERR_BALANCE_INVALID_USER", "Invalid user ID");
        }
    }
    
    public static class InvalidAmount extends BalanceException {
        public InvalidAmount() {
            super("ERR_BALANCE_INVALID_AMOUNT", "Amount must be between 1,000 and 1,000,000");
        }
    }
    
    public static class ConcurrencyConflict extends BalanceException {
        public ConcurrencyConflict() {
            super("ERR_BALANCE_CONCURRENCY_CONFLICT", "Concurrent balance update conflict");
        }
    }
    
    public static class Insufficient extends BalanceException {
        public Insufficient() {
            super("ERR_BALANCE_INSUFFICIENT", "Insufficient balance");
        }
    }
} 