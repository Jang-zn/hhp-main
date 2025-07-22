package kr.hhplus.be.server.api.docs.schema;

/**
 * API 에러 정보를 담는 레코드
 * HTTP 상태 코드, 에러 코드, 메시지를 포함
 */
public record ErrorInfo(
        int httpStatus,
        String errorCode,
        String message
) {}