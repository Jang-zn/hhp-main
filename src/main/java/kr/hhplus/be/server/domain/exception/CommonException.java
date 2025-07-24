package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

public class CommonException extends RuntimeException {
    private final String errorCode;
    
    public CommonException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    
    // 공통 예외들
    public static class InvalidPagination extends CommonException {
        public InvalidPagination() {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage());
        }
    }
    
    public static class ConcurrencyConflict extends CommonException {
        public ConcurrencyConflict() {
            super(ErrorCode.CONCURRENCY_ERROR.getCode(), ErrorCode.CONCURRENCY_ERROR.getMessage());
        }
    }

    public static class InvalidRequest extends CommonException {
        public InvalidRequest() {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage());
        }
    }

    public static class InvalidLimit extends CommonException {
        public InvalidLimit() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class LimitExceeded extends CommonException {
        public LimitExceeded() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class InvalidOffset extends CommonException {
        public InvalidOffset() {
            super(ErrorCode.VALUE_OUT_OF_RANGE.getCode(), ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }

    public static class InvalidInput extends CommonException {
        public InvalidInput() {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage());
        }
    }
} 