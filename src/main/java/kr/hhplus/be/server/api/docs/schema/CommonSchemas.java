package kr.hhplus.be.server.api.docs.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 공통 응답 스키마 정의
 * API 응답에서 사용되는 공통 구조체들을 정의
 */
public class CommonSchemas {

    /**
     * API 공통 응답 스키마
     */
    @Schema(description = "API 공통 응답")
    public record ApiResponse<T>(
            @Schema(description = "성공 여부", example = "true")
            boolean success,
            
            @Schema(description = "메시지", example = "요청 성공")
            String message,
            
            @Schema(description = "데이터")
            T data,
            
            @Schema(description = "응답 시간", example = "2025-07-22T13:40:00.123")
            LocalDateTime timestamp
    ) {}

    /**
     * 에러 응답 스키마
     */
    @Schema(description = "에러 응답")
    public record ErrorResponse(
            @Schema(description = "성공 여부", example = "false")
            boolean success,
            
            @Schema(description = "에러 코드", example = "INSUFFICIENT_BALANCE")
            String errorCode,
            
            @Schema(description = "에러 메시지", example = "잔액이 부족합니다")
            String message,
            
            @Schema(description = "응답 시간", example = "2025-07-22T13:40:00.123")
            LocalDateTime timestamp
    ) {}

    /**
     * 페이징 응답 스키마
     */
    @Schema(description = "페이징 응답")
    public record PageResponse<T>(
            @Schema(description = "데이터 목록")
            java.util.List<T> content,
            
            @Schema(description = "현재 페이지", example = "0")
            int page,
            
            @Schema(description = "페이지 크기", example = "10")
            int size,
            
            @Schema(description = "전체 요소 수", example = "100")
            long totalElements,
            
            @Schema(description = "전체 페이지 수", example = "10")
            int totalPages,
            
            @Schema(description = "첫 번째 페이지 여부", example = "true")
            boolean first,
            
            @Schema(description = "마지막 페이지 여부", example = "false")
            boolean last
    ) {}
}