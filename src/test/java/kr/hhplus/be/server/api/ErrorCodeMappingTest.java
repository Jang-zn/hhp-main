package kr.hhplus.be.server.api;

import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorCode와 Domain Exception 매핑 테스트")
class ErrorCodeMappingTest {

    @DisplayName("도메인 예외가 올바른 ErrorCode로 매핑된다")
    @ParameterizedTest
    @MethodSource("domainExceptionMappingProvider")
    void 도메인_예외가_올바른_ErrorCode로_매핑된다(RuntimeException exception, ErrorCode expectedErrorCode) {
        // When
        ErrorCode actualErrorCode = ErrorCode.fromDomainException(exception);
        
        // Then
        assertThat(actualErrorCode).isEqualTo(expectedErrorCode);
        assertThat(actualErrorCode.getCode()).isEqualTo(expectedErrorCode.getCode());
    }

    @DisplayName("ErrorCode로부터 적절한 HTTP 상태 코드가 반환된다")
    @ParameterizedTest
    @MethodSource("httpStatusMappingProvider")
    void ErrorCode로부터_적절한_HTTP_상태_코드가_반환된다(ErrorCode errorCode, HttpStatus expectedStatus) {
        // When
        HttpStatus actualStatus = ErrorCode.getHttpStatusFromErrorCode(errorCode);
        
        // Then
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("null 예외에 대해 INTERNAL_SERVER_ERROR가 반환된다")
    void null_예외에_대해_INTERNAL_SERVER_ERROR가_반환된다() {
        // When
        ErrorCode errorCode = ErrorCode.fromDomainException(null);
        
        // Then
        assertThat(errorCode).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("매핑되지 않은 예외에 대해 기본값이 반환된다")
    void 매핑되지_않은_예외에_대해_기본값이_반환된다() {
        // Given
        RuntimeException unknownException = new RuntimeException("알 수 없는 예외");
        
        // When
        ErrorCode errorCode = ErrorCode.fromDomainException(unknownException);
        
        // Then
        assertThat(errorCode).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    static Stream<Arguments> domainExceptionMappingProvider() {
        return Stream.of(
            // 사용자 관련 예외
            Arguments.of(new UserException.NotFound(), ErrorCode.USER_NOT_FOUND),
            Arguments.of(new UserException.InvalidUser(), ErrorCode.USER_NOT_FOUND),
            
            // 잔액 관련 예외
            Arguments.of(new BalanceException.NotFound(), ErrorCode.BALANCE_NOT_FOUND),
            Arguments.of(new BalanceException.InsufficientBalance(), ErrorCode.INSUFFICIENT_BALANCE),
            Arguments.of(new BalanceException.InvalidAmount(), ErrorCode.INVALID_AMOUNT),
            Arguments.of(new BalanceException.UserIdAndAmountRequired(), ErrorCode.INVALID_INPUT),
            
            // 상품 관련 예외
            Arguments.of(new ProductException.NotFound(), ErrorCode.PRODUCT_NOT_FOUND),
            Arguments.of(new ProductException.OutOfStock(), ErrorCode.PRODUCT_OUT_OF_STOCK),
            Arguments.of(new ProductException.InvalidReservation("잘못된 예약입니다"), ErrorCode.INVALID_RESERVATION),
            
            // 주문 관련 예외
            Arguments.of(new OrderException.NotFound(), ErrorCode.ORDER_NOT_FOUND),
            Arguments.of(new OrderException.Unauthorized(), ErrorCode.FORBIDDEN),
            Arguments.of(new OrderException.AlreadyPaid(), ErrorCode.ORDER_ALREADY_PAID),
            
            // 쿠폰 관련 예외
            Arguments.of(new CouponException.NotFound(), ErrorCode.COUPON_NOT_FOUND),
            Arguments.of(new CouponException.Expired(), ErrorCode.COUPON_EXPIRED),
            Arguments.of(new CouponException.CouponNotYetStarted(), ErrorCode.COUPON_NOT_YET_STARTED),
            Arguments.of(new CouponException.AlreadyIssued(), ErrorCode.COUPON_ALREADY_ISSUED),
            Arguments.of(new CouponException.OutOfStock(), ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED),
            Arguments.of(new CouponException.CouponStockExceeded(), ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED),
            
            // 동시성 관련 예외
            Arguments.of(new CommonException.ConcurrencyConflict(), ErrorCode.CONCURRENCY_ERROR)
        );
    }

    static Stream<Arguments> httpStatusMappingProvider() {
        return Stream.of(
            // 성공
            Arguments.of(ErrorCode.SUCCESS, HttpStatus.OK),
            
            // 잔액 관련
            Arguments.of(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.PAYMENT_REQUIRED),
            Arguments.of(ErrorCode.BALANCE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Arguments.of(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST),
            
            // 상품 관련
            Arguments.of(ErrorCode.PRODUCT_OUT_OF_STOCK, HttpStatus.CONFLICT),
            Arguments.of(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Arguments.of(ErrorCode.INVALID_RESERVATION, HttpStatus.CONFLICT),
            
            // 사용자 관련
            Arguments.of(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND),
            Arguments.of(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
            Arguments.of(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
            
            // 쿠폰 관련
            Arguments.of(ErrorCode.COUPON_EXPIRED, HttpStatus.GONE),
            Arguments.of(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED, HttpStatus.GONE),
            Arguments.of(ErrorCode.COUPON_NOT_FOUND, HttpStatus.NOT_FOUND),
            
            // 동시성 관련
            Arguments.of(ErrorCode.CONCURRENCY_ERROR, HttpStatus.CONFLICT),
            Arguments.of(ErrorCode.LOCK_ACQUISITION_FAILED, HttpStatus.CONFLICT),
            
            // 시스템 에러
            Arguments.of(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            Arguments.of(ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            
            // 입력 검증 에러
            Arguments.of(ErrorCode.INVALID_INPUT, HttpStatus.BAD_REQUEST),
            Arguments.of(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST)
        );
    }
}