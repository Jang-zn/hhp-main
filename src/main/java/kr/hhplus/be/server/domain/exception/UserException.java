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
    
    // 메시지 상수들
    public static class Messages {
        // Validation 메시지들
        public static final String INVALID_USER_ID = "사용자 ID는 필수입니다";
        public static final String INVALID_USER_ID_POSITIVE = "사용자 ID는 양수여야 합니다";
        public static final String INVALID_USER_NAME = "사용자명은 필수입니다";
        
        // UseCase 메시지들
        public static final String USER_ID_CANNOT_BE_NULL = "사용자 ID는 null일 수 없습니다";
        public static final String USER_NAME_CANNOT_BE_NULL = "사용자명은 null일 수 없습니다";
        public static final String FAILED_TO_RETRIEVE_USER = "사용자 조회에 실패했습니다";
        public static final String FAILED_TO_CREATE_USER = "사용자 생성에 실패했습니다";
        
        // Controller 메시지들
        public static final String USERID_CANNOT_BE_NULL = "사용자 ID는 null일 수 없습니다";
        
        // 비즈니스 로직 메시지들
        public static final String USER_NOT_FOUND = "사용자를 찾을 수 없습니다";
        public static final String UNAUTHORIZED_ACCESS = "접근 권한이 없습니다";
        public static final String USER_ALREADY_EXISTS = "이미 존재하는 사용자입니다";
        public static final String INVALID_USER_CREDENTIALS = "유효하지 않은 사용자 정보입니다";
        
        // Repository 레벨 validation 메시지들
        public static final String USER_CANNOT_BE_NULL = "사용자는 null일 수 없습니다";
        public static final String USER_ID_CANNOT_BE_NULL_REPO = "사용자 ID는 null일 수 없습니다";
        public static final String USER_NAME_CANNOT_BE_NULL_REPO = "사용자명은 null일 수 없습니다";
    }
    
    // 사용자 관련 예외들
    public static class InvalidUser extends UserException {
        public InvalidUser() {
            super("ERR_USER_INVALID", Messages.INVALID_USER_ID);
        }
    }
    
    public static class NotFound extends UserException {
        public NotFound() {
            super("ERR_USER_NOT_FOUND", Messages.USER_NOT_FOUND);
        }
    }
    
    public static class Unauthorized extends UserException {
        public Unauthorized() {
            super("ERR_USER_UNAUTHORIZED", Messages.UNAUTHORIZED_ACCESS);
        }
    }

    public static class InvalidUserId extends UserException {
        public InvalidUserId() {
            super("ERR_USER_INVALID_ID", Messages.INVALID_USER_ID);
        }
    }

    public static class InvalidUserIdPositive extends UserException {
        public InvalidUserIdPositive() {
            super("ERR_USER_INVALID_ID_POSITIVE", Messages.INVALID_USER_ID_POSITIVE);
        }
    }

    public static class InvalidUserName extends UserException {
        public InvalidUserName() {
            super("ERR_USER_INVALID_NAME", Messages.INVALID_USER_NAME);
        }
    }

    public static class UserIdCannotBeNull extends UserException {
        public UserIdCannotBeNull() {
            super("ERR_USER_ID_CANNOT_BE_NULL", Messages.USER_ID_CANNOT_BE_NULL);
        }
    }

    public static class UserNameCannotBeNull extends UserException {
        public UserNameCannotBeNull() {
            super("ERR_USER_NAME_CANNOT_BE_NULL", Messages.USER_NAME_CANNOT_BE_NULL);
        }
    }

    public static class FailedToRetrieveUser extends UserException {
        public FailedToRetrieveUser() {
            super("ERR_USER_FAILED_TO_RETRIEVE", Messages.FAILED_TO_RETRIEVE_USER);
        }
    }

    public static class FailedToCreateUser extends UserException {
        public FailedToCreateUser() {
            super("ERR_USER_FAILED_TO_CREATE", Messages.FAILED_TO_CREATE_USER);
        }
    }

    public static class UserAlreadyExists extends UserException {
        public UserAlreadyExists() {
            super("ERR_USER_ALREADY_EXISTS", Messages.USER_ALREADY_EXISTS);
        }
    }

    public static class InvalidUserCredentials extends UserException {
        public InvalidUserCredentials() {
            super("ERR_USER_INVALID_CREDENTIALS", Messages.INVALID_USER_CREDENTIALS);
        }
    }

    public static class UserCannotBeNull extends UserException {
        public UserCannotBeNull() {
            super("ERR_USER_CANNOT_BE_NULL", Messages.USER_CANNOT_BE_NULL);
        }
    }