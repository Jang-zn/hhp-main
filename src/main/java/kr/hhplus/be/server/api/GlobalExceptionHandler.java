package kr.hhplus.be.server.api;

import kr.hhplus.be.server.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
     * 입력값 검증 실패 예외 처리
     * @Valid, @NotNull 등의 Bean Validation 실패 시 발생
     * 
     * @param ex 검증 실패 예외
     * @return 400 Bad Request + 첫 번째 검증 오류 메시지
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(ErrorCode.INVALID_INPUT, errorMessage));
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