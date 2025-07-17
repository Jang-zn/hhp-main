package kr.hhplus.be.server.api;

import java.time.LocalDateTime;

/**
 * API 응답의 표준화된 래퍼 클래스
 * 
 * 모든 API 응답을 일관된 형태로 감싸서 클라이언트가 예측 가능한 응답 구조를 받을 수 있게 한다.
 * 
 * 응답 형태:
 * {
 *   "success": true/false,
 *   "message": "성공/실패 메시지",
 *   "data": 실제 응답 데이터 (성공 시에만),
 *   "timestamp": "2024-01-01T12:00:00"
 * }
 * 
 * 사용 예:
 * - 성공: CommonResponse.success(userDto) -> SuccessResponseAdvice에서 자동 생성
 * - 실패: CommonResponse.failure("오류 메시지") -> GlobalExceptionHandler에서 자동 생성
 */
public class CommonResponse<T> {
    private boolean success;      // 요청 성공 여부
    private String message;       // 응답 메시지 (성공/실패 메시지)
    private T data;              // 실제 응답 데이터 (성공 시에만 존재)
    private LocalDateTime timestamp; // 응답 생성 시간

    /**
     * 성공 응답용 private 생성자
     * @param message 성공 메시지
     * @param data 응답 데이터
     */
    private CommonResponse(String message, T data) {
        this.success = true;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 실패 응답용 private 생성자
     * @param message 실패 메시지
     */
    private CommonResponse(String message) {
        this.success = false;
        this.message = message;
        this.data = null;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 성공 응답 생성 (커스텀 메시지)
     * @param message 성공 메시지
     * @param data 응답 데이터
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(message, data);
    }

    /**
     * 성공 응답 생성 (기본 메시지 "요청이 성공했습니다")
     * SuccessResponseAdvice에서 주로 사용
     * @param data 응답 데이터
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(ApiMessage.SUCCESS.getMessage(), data);
    }

    /**
     * 성공 응답 생성 (데이터 없음, void 메서드용)
     * SuccessResponseAdvice에서 void 반환 메서드에 사용
     * @return CommonResponse 객체
     */
    public static CommonResponse<Void> success() {
        return new CommonResponse<>(ApiMessage.SUCCESS.getMessage(), null);
    }

    /**
     * 실패 응답 생성
     * GlobalExceptionHandler에서 예외 발생 시 사용
     * @param message 실패 메시지
     * @return CommonResponse 객체
     */
    public static <T> CommonResponse<T> failure(String message) {
        return new CommonResponse<>(message);
    }
    
    // JSON 직렬화를 위한 Getter 메서드들
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }
} 