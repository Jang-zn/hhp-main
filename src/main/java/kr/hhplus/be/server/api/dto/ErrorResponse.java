package kr.hhplus.be.server.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 표준 오류 응답 모델 (Swagger 전용)
 * <p>
 * CommonResponse.failure(...) 구조와 동일하지만, Swagger 문서를 위한 경량 모델이다.
 */
@Schema(name = "ErrorResponse", description = "실패 응답 모델")
public class ErrorResponse {

    @Schema(description = "요청 성공 여부", example = "false")
    private final boolean success = false;

    @Schema(description = "에러 메시지", example = "Error Message")
    private String message;

    @Schema(description = "응답 시간", example = "2025-07-17T13:40:00.123")
    private LocalDateTime timestamp;

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // Getter (Swagger는 getter 기반으로 스키마를 생성한다)
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
} 