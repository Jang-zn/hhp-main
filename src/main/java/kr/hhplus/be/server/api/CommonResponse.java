package kr.hhplus.be.server.api;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.dto.response.ProductResponse;

/**
 * API 응답의 표준화된 래퍼 클래스
 * 
 * 모든 API 응답을 일관된 형태로 감싸서 클라이언트가 예측 가능한 응답 구조를 받을 수 있게 한다.
 * ErrorCode Enum을 활용하여 중앙 집중식 에러 관리를 지원한다.
 * 
 * 응답 형태:
 * {
 *   "code": "S001" (성공) / "U001" (에러),
 *   "message": "성공/실패 메시지",
 *   "data": 실제 응답 데이터 (성공 시에만),
 *   "timestamp": "2024-01-01T12:00:00"
 * }
 * 
 * 사용 예:
 * - 성공: CommonResponse.success(userDto) -> SuccessResponseAdvice에서 자동 생성
 * - 실패: CommonResponse.failure(ErrorCode.USER_NOT_FOUND) -> GlobalExceptionHandler에서 자동 생성
 */
@Schema(name = "CommonResponse", description = "표준 성공/실패 응답 래퍼")
public class CommonResponse<T> {
    
    @Schema(description = "응답 코드 (성공: S001, 에러: 도메인별 코드)", example = "S001")
    private final String code;        // 응답 코드 (성공/에러 코드)

    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    private final String message;     // 응답 메시지

    @Schema(description = "응답 데이터", anyOf = {
        BalanceResponse.class, 
        CouponResponse.class, 
        OrderResponse.class, 
        PaymentResponse.class, 
        ProductResponse.class})
    private final T data;            // 실제 응답 데이터

    @Schema(description = "응답 시간", example = "2025-07-17T13:40:00.123")
    private final LocalDateTime timestamp; // 응답 생성 시간

    /**
     * 성공 응답용 private 생성자
     * @param data 응답 데이터
     */
    private CommonResponse(T data) {
        this.code = ErrorCode.SUCCESS.getCode();
        this.message = ErrorCode.SUCCESS.getMessage();
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 성공 응답용 private 생성자 (커스텀 메시지)
     * @param message 커스텀 성공 메시지
     * @param data 응답 데이터
     */
    private CommonResponse(String message, T data) {
        this.code = ErrorCode.SUCCESS.getCode();
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 실패 응답용 private 생성자
     * @param errorCode ErrorCode Enum
     */
    private CommonResponse(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = null;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 실패 응답용 private 생성자 (커스텀 메시지)
     * @param errorCode ErrorCode Enum
     * @param customMessage 커스텀 에러 메시지
     */
    private CommonResponse(ErrorCode errorCode, String customMessage) {
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.data = null;
        this.timestamp = LocalDateTime.now();
    }

    // =========================== 성공 응답 생성 메서드 ===========================
    
    /**
     * 성공 응답 생성 (기본 메시지)
     * SuccessResponseAdvice에서 주로 사용
     * @param data 응답 데이터
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(data);
    }

    /**
     * 성공 응답 생성 (데이터 없음, void 메서드용)
     * SuccessResponseAdvice에서 void 반환 메서드에 사용
     * @return CommonResponse 객체
     */
    public static CommonResponse<Void> success() {
        return new CommonResponse<>((Void) null);
    }

    /**
     * 성공 응답 생성 (커스텀 메시지)
     * @param message 커스텀 성공 메시지
     * @param data 응답 데이터
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(message, data);
    }
    
    // =========================== 실패 응답 생성 메서드 ===========================

    /**
     * 실패 응답 생성 (ErrorCode 사용)
     * GlobalExceptionHandler에서 도메인 예외 발생 시 사용
     * @param errorCode ErrorCode Enum
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> failure(ErrorCode errorCode) {
        return new CommonResponse<>(errorCode);
    }

    /**
     * 실패 응답 생성 (ErrorCode + 커스텀 메시지)
     * 기본 에러 메시지를 상황에 맞게 커스터마이징할 때 사용
     * @param errorCode ErrorCode Enum
     * @param customMessage 커스텀 에러 메시지
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> failure(ErrorCode errorCode, String customMessage) {
        return new CommonResponse<>(errorCode, customMessage);
    }

    /**
     * 실패 응답 생성 (레거시 호환용)
     * 기존 코드와의 호환성을 위해 유지, 점진적으로 ErrorCode 사용으로 마이그레이션 권장
     * @param message 실패 메시지
     * @return CommonResponse 객체
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static <T> CommonResponse<T> failure(String message) {
        return new CommonResponse<>(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
    
    // =========================== 유틸리티 메서드 ===========================
    
    /**
     * 성공 응답인지 확인
     * @return 성공 응답 여부
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }
    
    /**
     * 실패 응답인지 확인
     * @return 실패 응답 여부
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFailure() {
        return !isSuccess();
    }
    
    /**
     * 클라이언트 에러인지 확인 (4xx 계열)
     * @return 클라이언트 에러 여부
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isClientError() {
        ErrorCode errorCode = ErrorCode.findByCode(this.code);
        return errorCode != null && errorCode.isClientError();
    }
    
    /**
     * 시스템 에러인지 확인 (5xx 계열)
     * @return 시스템 에러 여부
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isServerError() {
        ErrorCode errorCode = ErrorCode.findByCode(this.code);
        return errorCode != null && errorCode.isSystemError();
    }
    
    // =========================== Getter 메서드 ===========================
    
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }
} 