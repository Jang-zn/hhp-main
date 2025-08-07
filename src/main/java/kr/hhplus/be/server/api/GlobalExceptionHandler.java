package kr.hhplus.be.server.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import kr.hhplus.be.server.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

/**
 * 전역 예외 처리 클래스
 * 
 * 애플리케이션에서 발생하는 모든 예외를 캐치하여 표준화된 오류 응답을 생성한다.
 * Spring의 @RestControllerAdvice를 사용하여 컨트롤러에서 발생한 예외를 중앙에서 처리한다.
 * 
 * 처리 과정:
 * 1. 컨트롤러에서 예외 발생 (예: ProductException.NotFound)
 * 2. GlobalExceptionHandler가 예외를 캐치
 * 3. 예외 타입에 따른 적절한 HTTP 상태 코드 결정
 * 4. CommonResponse.failure()로 표준화된 오류 응답 생성
 * 5. ResponseEntity로 래핑하여 반환
 * 
 * 응답 예시:
 * {
 *   "success": false,
 *   "message": "상품을 찾을 수 없습니다",
 *   "errorCode": "ERR_PRODUCT_NOT_FOUND",
 *   "data": null,
 *   "timestamp": "2024-01-01T12:00:00"
 * }
 * 
 * 적용 범위: kr.hhplus.be.server.api.controller 패키지의 모든 컨트롤러
 */
@RestControllerAdvice(basePackages = "kr.hhplus.be.server.api.controller")
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 예외 처리
     * 도메인에서 정의한 커스텀 예외들을 처리한다.
     * 
     * @param ex 발생한 비즈니스 예외
     * @return 표준화된 오류 응답 (HTTP 상태 코드 + CommonResponse)
     */
    @ExceptionHandler({BalanceException.class, CouponException.class, OrderException.class, PaymentException.class, ProductException.class, UserException.class, CommonException.class})
    public ResponseEntity<CommonResponse<Object>> handleBusinessException(RuntimeException ex) {
        // ErrorCode 자동 매핑
        ErrorCode errorCode = ErrorCode.fromDomainException(ex);
        HttpStatus status = ErrorCode.getHttpStatusFromErrorCode(errorCode);
        
        return ResponseEntity.status(status).body(
            CommonResponse.failure(errorCode, ex.getMessage())
        );
    }

    /**
     * 입력값 검증 실패 예외 처리 (@Valid 어노테이션)
     * @Valid, @NotNull 등의 Bean Validation 실패 시 발생
     * 모든 Bean Validation 오류를 INVALID_INPUT으로 통일 처리
     * 
     * @param ex 검증 실패 예외
     * @return 400 Bad Request + INVALID_INPUT ErrorCode
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            CommonResponse.failure(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage())
        );
    }
    
    /**
     * 경로 변수 검증 실패 예외 처리 (@Validated 어노테이션)
     * @PathVariable, @RequestParam에 대한 Bean Validation 실패 시 발생
     * 모든 경로 변수 검증 오류를 INVALID_INPUT으로 통일 처리
     * 
     * @param ex 제약 조건 위반 예외
     * @return 400 Bad Request + INVALID_INPUT ErrorCode
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponse<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            CommonResponse.failure(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage())
        );
    }

    /**
     * 타입 변환 실패 예외 처리
     * URL 경로에서 문자열을 숫자로 변환할 때 실패하는 경우 발생
     * 
     * @param ex 타입 변환 실패 예외
     * @return 400 Bad Request + INVALID_INPUT ErrorCode
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            CommonResponse.failure(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage())
        );
    }

    /**
     * HTTP 메시지 읽기 실패 예외 처리
     * JSON 파싱 실패, 잘못된 형식의 요청 본문 등에서 발생
     * 
     * @param ex HTTP 메시지 읽기 실패 예외
     * @return 400 Bad Request + INVALID_INPUT ErrorCode
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            CommonResponse.failure(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage())
        );
    }

    /**
     * 공통 예외 처리 - InvalidRequest
     * 
     * @param ex InvalidRequest 예외
     * @return 400 Bad Request + ErrorCode 기반 메시지
     */
    @ExceptionHandler(CommonException.InvalidRequest.class)
    public ResponseEntity<CommonResponse<Object>> handleInvalidRequestException(CommonException.InvalidRequest ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * 공통 예외 처리 - InvalidPagination
     * 
     * @param ex InvalidPagination 예외
     * @return 400 Bad Request + ErrorCode 기반 메시지
     */
    @ExceptionHandler(CommonException.InvalidPagination.class)
    public ResponseEntity<CommonResponse<Object>> handleInvalidPaginationException(CommonException.InvalidPagination ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * 공통 예외 처리 - ConcurrencyConflict
     * 
     * @param ex ConcurrencyConflict 예외
     * @return 409 Conflict + ErrorCode 기반 메시지
     */
    @ExceptionHandler(CommonException.ConcurrencyConflict.class)
    public ResponseEntity<CommonResponse<Object>> handleConcurrencyConflictException(CommonException.ConcurrencyConflict ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.failure(ErrorCode.CONCURRENCY_ERROR));
    }

    /**
     * 잘못된 인자 예외 처리
     * @param ex 잘못된 인자 예외
     * @return 400 Bad Request + 예외 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(ErrorCode.INVALID_INPUT, ex.getMessage()));
    }

    /**
     * 잘못된 상태 예외 처리
     * @param ex 잘못된 상태 예외
     * @return 400 Bad Request + 예외 메시지
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonResponse<Object>> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * 예상하지 못한 모든 예외 처리 (최후의 보루)
     * 위에서 처리되지 않은 모든 예외를 캐치한다.
     * 
     * @param ex 예상하지 못한 예외
     * @return 500 Internal Server Error + 일반적인 오류 메시지
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Object>> handleAllUncaughtException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(CommonResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // 기존 매핑 메서드들은 ErrorCode.fromDomainException()으로 대체됨
} 