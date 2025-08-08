package kr.hhplus.be.server.api.docs.config;

import kr.hhplus.be.server.api.docs.dto.ApiResponseDto;
import kr.hhplus.be.server.api.docs.schema.ErrorInfo;
import kr.hhplus.be.server.api.docs.schema.ErrorSchemas;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * API 응답 예시 자동 생성기
 * 에러 코드별 표준화된 응답 예시를 생성
 */
@Component
public class ApiResponseGenerator {

    /**
     * 타입 안전한 성공 응답 예시 생성
     */
    public <T> ApiResponseDto<T> generateSuccessResponse(T data) {
        return ApiResponseDto.success("요청 성공", data);
    }

    /**
     * 타입 안전한 에러 응답 예시 생성
     */
    public <T> ApiResponseDto<T> generateErrorResponse(String errorCode, String message) {
        return ApiResponseDto.error(errorCode, message);
    }

    /**
     * 도메인별 에러 응답 예시 생성
     */
    public java.util.Map<String, ApiResponseDto<Object>> generateDomainErrorExamples(String domain) {
        java.util.Map<String, ApiResponseDto<Object>> examples = new HashMap<>();
        
        switch (domain.toLowerCase()) {
            case "balance" -> {
                ErrorSchemas.BALANCE_ERRORS.forEach((exceptionClass, errorInfo) -> {
                    examples.put(errorInfo.errorCode(), generateErrorResponse(errorInfo.errorCode(), errorInfo.message()));
                });
            }
            case "coupon" -> {
                ErrorSchemas.COUPON_ERRORS.forEach((exceptionClass, errorInfo) -> {
                    examples.put(errorInfo.errorCode(), generateErrorResponse(errorInfo.errorCode(), errorInfo.message()));
                });
            }
            case "product" -> {
                ErrorSchemas.PRODUCT_ERRORS.forEach((exceptionClass, errorInfo) -> {
                    examples.put(errorInfo.errorCode(), generateErrorResponse(errorInfo.errorCode(), errorInfo.message()));
                });
            }
            case "order" -> {
                ErrorSchemas.ORDER_ERRORS.forEach((exceptionClass, errorInfo) -> {
                    examples.put(errorInfo.errorCode(), generateErrorResponse(errorInfo.errorCode(), errorInfo.message()));
                });
            }
        }
        
        // 공통 에러도 추가
        ErrorSchemas.COMMON_ERRORS.forEach((exceptionClass, errorInfo) -> {
            examples.put(errorInfo.errorCode(), generateErrorResponse(errorInfo.errorCode(), errorInfo.message()));
        });
        
        return examples;
    }

    /**
     * HTTP 상태 코드별 에러 응답 예시 생성
     */
    public java.util.Map<String, ApiResponseDto<Object>> generateHttpStatusErrorExamples() {
        java.util.Map<String, ApiResponseDto<Object>> examples = new HashMap<>();
        
        // 400 Bad Request
        examples.put("400", generateErrorResponse("INVALID_REQUEST", "잘못된 요청입니다"));
        
        // 402 Payment Required (잔액 부족)
        examples.put("402", generateErrorResponse("INSUFFICIENT_BALANCE", "잔액이 부족합니다"));
        
        // 403 Forbidden
        examples.put("403", generateErrorResponse("ORDER_UNAUTHORIZED", "주문에 대한 접근 권한이 없습니다"));
        
        // 404 Not Found
        examples.put("404", generateErrorResponse("RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다"));
        
        // 409 Conflict
        examples.put("409", generateErrorResponse("PRODUCT_OUT_OF_STOCK", "상품이 품절되었습니다"));
        
        // 410 Gone (만료/비활성화)
        examples.put("410", generateErrorResponse("COUPON_EXPIRED", "쿠폰이 만료되었습니다"));
        
        // 500 Internal Server Error
        examples.put("500", generateErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"));
        
        return examples;
    }

    /**
     * 특정 에러 코드의 응답 예시 생성
     */
    public ApiResponseDto<Object> generateSpecificErrorExample(String errorCode) {
        // 모든 도메인 에러 스키마를 리스트로 관리하여 반복 제거
        var allErrorSchemas = java.util.List.of(
            ErrorSchemas.BALANCE_ERRORS,
            ErrorSchemas.COUPON_ERRORS,
            ErrorSchemas.PRODUCT_ERRORS,
            ErrorSchemas.ORDER_ERRORS,
            ErrorSchemas.USER_ERRORS,
            ErrorSchemas.COMMON_ERRORS
        );
        
        return allErrorSchemas.stream()
                .flatMap(schema -> schema.values().stream())
                .filter(errorInfo -> errorInfo.errorCode().equals(errorCode))
                .findFirst()
                .map(errorInfo -> generateErrorResponse(errorInfo.errorCode(), errorInfo.message()))
                .orElse(generateErrorResponse(errorCode, "알 수 없는 오류가 발생했습니다"));
    }
    
}