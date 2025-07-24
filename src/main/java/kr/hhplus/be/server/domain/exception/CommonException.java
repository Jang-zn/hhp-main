package kr.hhplus.be.server.domain.exception;

public class CommonException extends RuntimeException {
    private final String errorCode;
    
    public CommonException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들 (공통)
        public static final String INVALID_LIMIT = "limit은 양수여야 합니다";
        public static final String LIMIT_EXCEEDED = "limit은 100을 초과할 수 없습니다";
        public static final String INVALID_OFFSET = "offset은 0 이상이어야 합니다";
        
        // UseCase 메시지들 (공통)
        public static final String REQUEST_CANNOT_BE_NULL = "요청은 null일 수 없습니다";
        public static final String INVALID_PAGINATION_PARAMETERS = "잘못된 페이징 매개변수입니다";
        public static final String CONCURRENCY_CONFLICT = "동시성 충돌이 발생했습니다";
    }
    
    // 공통 예외들
    public static class InvalidPagination extends CommonException {
        public InvalidPagination() {
            super("ERR_COMMON_INVALID_PAGINATION", Messages.INVALID_PAGINATION_PARAMETERS);
        }
    }
    
    public static class ConcurrencyConflict extends CommonException {
        public ConcurrencyConflict() {
            super("ERR_COMMON_CONCURRENCY_CONFLICT", Messages.CONCURRENCY_CONFLICT);
        }
    }

    public static class InvalidRequest extends CommonException {
        public InvalidRequest() {
            super("ERR_COMMON_INVALID_REQUEST", Messages.REQUEST_CANNOT_BE_NULL);
        }
    }

    public static class InvalidLimit extends CommonException {
        public InvalidLimit() {
            super("ERR_COMMON_INVALID_LIMIT", Messages.INVALID_LIMIT);
        }
    }

    public static class LimitExceeded extends CommonException {
        public LimitExceeded() {
            super("ERR_COMMON_LIMIT_EXCEEDED", Messages.LIMIT_EXCEEDED);
        }
    }

    public static class InvalidOffset extends CommonException {
        public InvalidOffset() {
            super("ERR_COMMON_INVALID_OFFSET", Messages.INVALID_OFFSET);
        }
    }
} 