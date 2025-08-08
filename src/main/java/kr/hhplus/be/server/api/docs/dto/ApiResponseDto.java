package kr.hhplus.be.server.api.docs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 타입 안전한 API 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponseDto", description = "API 공통 응답 구조")
public class ApiResponseDto<T> {
    
    @Schema(description = "요청 성공 여부", example = "true")
    private boolean success;
    
    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다")
    private String message;
    
    @Schema(description = "응답 데이터")
    private T data;
    
    @Schema(description = "에러 코드 (에러 시에만 포함)", example = "INSUFFICIENT_BALANCE")
    private String errorCode;
    
    @Schema(description = "응답 시간", example = "2025-07-22T13:40:00.123")
    private LocalDateTime timestamp;
    
    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(
            true,
            message,
            data,
            null,
            LocalDateTime.now()
        );
    }
    
    /**
     * 성공 응답 생성 (기본 메시지)
     */
    public static <T> ApiResponseDto<T> success(T data) {
        return success("요청 성공", data);
    }
    
    /**
     * 에러 응답 생성
     */
    public static <T> ApiResponseDto<T> error(String errorCode, String message) {
        return new ApiResponseDto<>(
            false,
            message,
            null,
            errorCode,
            LocalDateTime.now()
        );
    }
    
    /**
     * 에러 응답 생성 (기본 메시지)
     */
    public static <T> ApiResponseDto<T> error(String errorCode) {
        return error(errorCode, "알 수 없는 오류가 발생했습니다");
    }
}