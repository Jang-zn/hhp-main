package kr.hhplus.be.server.domain.exception;

import kr.hhplus.be.server.api.ErrorCode;

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
    public static class InvalidUser extends UserException {
        public InvalidUser() {
            super(ErrorCode.INVALID_USER_ID.getCode(), ErrorCode.INVALID_USER_ID.getMessage());
        }
    }
    
    public static class NotFound extends UserException {
        public NotFound() {
            super(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
    }
    
    public static class Unauthorized extends UserException {
        public Unauthorized() {
            super(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
        }
    }

    public static class InvalidUserId extends UserException {
        public InvalidUserId() {
            super(ErrorCode.INVALID_USER_ID.getCode(), ErrorCode.INVALID_USER_ID.getMessage());
        }
    }

    public static class InvalidUserIdPositive extends UserException {
        public InvalidUserIdPositive() {
            super(ErrorCode.INVALID_USER_ID.getCode(), ErrorCode.INVALID_USER_ID.getMessage());
        }
    }

    public static class InvalidUserName extends UserException {
        public InvalidUserName() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class UserIdCannotBeNull extends UserException {
        public UserIdCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class UserNameCannotBeNull extends UserException {
        public UserNameCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }

    public static class FailedToRetrieveUser extends UserException {
        public FailedToRetrieveUser() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class FailedToCreateUser extends UserException {
        public FailedToCreateUser() {
            super(ErrorCode.DATABASE_ERROR.getCode(), ErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    public static class UserAlreadyExists extends UserException {
        public UserAlreadyExists() {
            super(ErrorCode.USER_ALREADY_EXISTS.getCode(), ErrorCode.USER_ALREADY_EXISTS.getMessage());
        }
    }

    public static class InvalidUserCredentials extends UserException {
        public InvalidUserCredentials() {
            super(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage());
        }
    }

    public static class UserCannotBeNull extends UserException {
        public UserCannotBeNull() {
            super(ErrorCode.MISSING_REQUIRED_FIELD.getCode(), ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }
    }
}