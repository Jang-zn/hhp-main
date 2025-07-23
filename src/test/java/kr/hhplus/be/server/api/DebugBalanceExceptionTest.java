package kr.hhplus.be.server.api;

import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("잔액 예외 디버깅 테스트")
class DebugBalanceExceptionTest {

    @Test
    @DisplayName("UserException.InvalidUser의 ErrorCode 매핑과 HTTP 상태 코드 확인")
    void debugUserInvalidUser() {
        // Given
        RuntimeException ex = new UserException.InvalidUser();
        
        // When
        ErrorCode errorCode = ErrorCode.fromDomainException(ex);
        HttpStatus httpStatus = ErrorCode.getHttpStatusFromErrorCode(errorCode);
        
        // Then
        System.out.println("Exception: " + ex.getClass().getSimpleName());
        System.out.println("ErrorCode: " + errorCode);
        System.out.println("ErrorCode.getCode(): " + errorCode.getCode());
        System.out.println("ErrorCode.getMessage(): " + errorCode.getMessage());
        System.out.println("HttpStatus: " + httpStatus);
        
        assertThat(errorCode).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(httpStatus).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BalanceException.InvalidAmount의 ErrorCode 매핑과 HTTP 상태 코드 확인")
    void debugBalanceInvalidAmount() {
        // Given
        RuntimeException ex = new BalanceException.InvalidAmount();
        
        // When
        ErrorCode errorCode = ErrorCode.fromDomainException(ex);
        HttpStatus httpStatus = ErrorCode.getHttpStatusFromErrorCode(errorCode);
        
        // Then
        System.out.println("Exception: " + ex.getClass().getSimpleName());
        System.out.println("ErrorCode: " + errorCode);
        System.out.println("ErrorCode.getCode(): " + errorCode.getCode());
        System.out.println("ErrorCode.getMessage(): " + errorCode.getMessage());
        System.out.println("HttpStatus: " + httpStatus);
        
        assertThat(errorCode).isEqualTo(ErrorCode.INVALID_AMOUNT);
        assertThat(httpStatus).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}