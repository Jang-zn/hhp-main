package kr.hhplus.be.server.api;

import kr.hhplus.be.server.domain.exception.BalanceException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.PaymentException;
import kr.hhplus.be.server.domain.exception.ProductException;
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
    @ExceptionHandler({BalanceException.class, CouponException.class, OrderException.class, PaymentException.class, ProductException.class})
    public ResponseEntity<CommonResponse<Object>> handleBusinessException(RuntimeException ex) {
        HttpStatus status = getStatusFromException(ex);
        return ResponseEntity.status(status).body(CommonResponse.failure(ex.getMessage()));
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.failure(errorMessage));
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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(CommonResponse.failure("알 수 없는 오류가 발생했습니다."));
    }

    /**
     * 예외 타입에 따른 HTTP 상태 코드 매핑
     * 비즈니스 의미에 맞는 적절한 HTTP 상태 코드를 반환한다.
     * 
     * @param ex 발생한 예외
     * @return 해당 예외에 적합한 HTTP 상태 코드
     */
    private HttpStatus getStatusFromException(RuntimeException ex) {
        // 잔액 부족 관련 -> 402 Payment Required
        if (ex instanceof BalanceException.InsufficientBalance || ex instanceof PaymentException.InsufficientBalance) return HttpStatus.PAYMENT_REQUIRED;
        
        // 쿠폰 만료/소진 -> 410 Gone (더 이상 사용할 수 없음)
        if (ex instanceof CouponException.Expired || ex instanceof CouponException.OutOfStock) return HttpStatus.GONE;
        
        // 권한 없음 -> 403 Forbidden
        if (ex instanceof OrderException.Unauthorized) return HttpStatus.FORBIDDEN;
        
        // 리소스 없음 -> 404 Not Found
        if (ex instanceof ProductException.NotFound || ex instanceof OrderException.NotFound || ex instanceof CouponException.NotFound || ex instanceof PaymentException.OrderNotFound) return HttpStatus.NOT_FOUND;
        
        // 동시성 충돌, 재고 부족 -> 409 Conflict (리소스 상태 충돌)
        if (ex instanceof BalanceException.ConcurrencyConflict || ex instanceof OrderException.ConcurrencyConflict || ex instanceof PaymentException.ConcurrencyConflict || ex instanceof ProductException.OutOfStock) return HttpStatus.CONFLICT;
        
        // 기타 모든 경우 -> 400 Bad Request
        return HttpStatus.BAD_REQUEST;
    }
} 