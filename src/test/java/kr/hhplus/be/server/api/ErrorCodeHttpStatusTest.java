package kr.hhplus.be.server.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorCode HTTP 상태 코드 매핑 검증")
class ErrorCodeHttpStatusTest {

    @Test
    @DisplayName("USER_NOT_FOUND는 404 NOT_FOUND를 반환한다")
    void userNotFound_shouldReturn404() {
        HttpStatus status = ErrorCode.getHttpStatusFromErrorCode(ErrorCode.USER_NOT_FOUND);
        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BALANCE_NOT_FOUND는 404 NOT_FOUND를 반환한다")
    void balanceNotFound_shouldReturn404() {
        HttpStatus status = ErrorCode.getHttpStatusFromErrorCode(ErrorCode.BALANCE_NOT_FOUND);
        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("INVALID_AMOUNT는 400 BAD_REQUEST를 반환한다")
    void invalidAmount_shouldReturn400() {
        HttpStatus status = ErrorCode.getHttpStatusFromErrorCode(ErrorCode.INVALID_AMOUNT);
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ORDER_ALREADY_PAID는 400 BAD_REQUEST를 반환한다")
    void orderAlreadyPaid_shouldReturn400() {
        HttpStatus status = ErrorCode.getHttpStatusFromErrorCode(ErrorCode.ORDER_ALREADY_PAID);
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}