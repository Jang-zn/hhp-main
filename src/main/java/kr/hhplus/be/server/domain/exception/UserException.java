package kr.hhplus.be.server.domain.exception;

public class UserException extends RuntimeException {
    private final String errorCode;
    
    public UserException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 사용자 관련 예외들
    public static class NotFound extends UserException {
        public NotFound() {
            super("ERR_USER_NOT_FOUND", "User not found");
        }
    }
    
    public static class Unauthorized extends UserException {
        public Unauthorized() {
            super("ERR_USER_UNAUTHORIZED", "Unauthorized access");
        }
    }
}