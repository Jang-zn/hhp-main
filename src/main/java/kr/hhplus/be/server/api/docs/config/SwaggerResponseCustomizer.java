package kr.hhplus.be.server.api.docs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import kr.hhplus.be.server.api.docs.annotation.*;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * Swagger 응답 커스터마이저
 * 에러 코드별 예시 응답을 자동으로 추가
 */
@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    private final ApiResponseGenerator responseGenerator;

    public SwaggerResponseCustomizer(ApiResponseGenerator responseGenerator) {
        this.responseGenerator = responseGenerator;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        addErrorResponses(operation, handlerMethod);
        return operation;
    }

    private void addErrorResponses(Operation operation, HandlerMethod handlerMethod) {
        String domain = extractDomainFromMethod(handlerMethod);
        
        // 기본 에러 응답들 추가
        addStandardErrorResponses(operation);
        
        // 도메인별 에러 응답 추가
        if (domain != null) {
            addDomainSpecificErrorResponses(operation, domain);
        }
        
        // 어노테이션에 명시된 에러 코드들 추가
        addAnnotationSpecificErrorResponses(operation, handlerMethod);
    }

    private String extractDomainFromMethod(HandlerMethod handlerMethod) {
        // 어노테이션 기반으로 도메인 추출
        if (handlerMethod.hasMethodAnnotation(BalanceApiDocs.class)) {
            return "balance";
        } else if (handlerMethod.hasMethodAnnotation(CouponApiDocs.class)) {
            return "coupon";
        } else if (handlerMethod.hasMethodAnnotation(ProductApiDocs.class)) {
            return "product";
        } else if (handlerMethod.hasMethodAnnotation(OrderApiDocs.class)) {
            return "order";
        }
        
        // 컨트롤러 클래스명으로도 추출 시도
        String className = handlerMethod.getBeanType().getSimpleName().toLowerCase();
        if (className.contains("balance")) return "balance";
        if (className.contains("coupon")) return "coupon";
        if (className.contains("product")) return "product";
        if (className.contains("order")) return "order";
        
        return null;
    }

    private void addStandardErrorResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        // 400 Bad Request
        if (!responses.containsKey("400")) {
            responses.addApiResponse("400", createErrorResponse(
                "잘못된 요청",
                responseGenerator.generateErrorResponse("INVALID_REQUEST", "잘못된 요청입니다")
            ));
        }

        // 500 Internal Server Error
        if (!responses.containsKey("500")) {
            responses.addApiResponse("500", createErrorResponse(
                "서버 오류",
                responseGenerator.generateErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다")
            ));
        }
    }

    private void addDomainSpecificErrorResponses(Operation operation, String domain) {
        ApiResponses responses = operation.getResponses();
        Map<String, Map<String, Object>> domainErrors = responseGenerator.generateDomainErrorExamples(domain);

        switch (domain) {
            case "balance" -> {
                // 402 Payment Required (잔액 부족)
                responses.addApiResponse("402", createErrorResponse(
                    "잔액 부족",
                    responseGenerator.generateSpecificErrorExample("INSUFFICIENT_BALANCE")
                ));
                // 404 Not Found (잔액 정보 없음)
                responses.addApiResponse("404", createErrorResponse(
                    "잔액 정보 없음",
                    responseGenerator.generateSpecificErrorExample("BALANCE_NOT_FOUND")
                ));
            }
            case "coupon" -> {
                // 404 Not Found (쿠폰 없음)
                responses.addApiResponse("404", createErrorResponse(
                    "쿠폰 없음",
                    responseGenerator.generateSpecificErrorExample("COUPON_NOT_FOUND")
                ));
                // 409 Conflict (이미 발급됨)
                responses.addApiResponse("409", createErrorResponse(
                    "쿠폰 중복 발급",
                    responseGenerator.generateSpecificErrorExample("COUPON_ALREADY_ISSUED")
                ));
                // 410 Gone (쿠폰 만료)
                responses.addApiResponse("410", createErrorResponse(
                    "쿠폰 만료",
                    responseGenerator.generateSpecificErrorExample("COUPON_EXPIRED")
                ));
            }
            case "product" -> {
                // 404 Not Found (상품 없음)
                responses.addApiResponse("404", createErrorResponse(
                    "상품 없음",
                    responseGenerator.generateSpecificErrorExample("PRODUCT_NOT_FOUND")
                ));
                // 409 Conflict (재고 부족)
                responses.addApiResponse("409", createErrorResponse(
                    "재고 부족",
                    responseGenerator.generateSpecificErrorExample("PRODUCT_OUT_OF_STOCK")
                ));
            }
            case "order" -> {
                // 403 Forbidden (권한 없음)
                responses.addApiResponse("403", createErrorResponse(
                    "접근 권한 없음",
                    responseGenerator.generateSpecificErrorExample("ORDER_UNAUTHORIZED")
                ));
                // 404 Not Found (주문 없음)
                responses.addApiResponse("404", createErrorResponse(
                    "주문 없음",
                    responseGenerator.generateSpecificErrorExample("ORDER_NOT_FOUND")
                ));
                // 409 Conflict (이미 결제됨)
                responses.addApiResponse("409", createErrorResponse(
                    "이미 결제됨",
                    responseGenerator.generateSpecificErrorExample("ORDER_ALREADY_PAID")
                ));
            }
        }
    }

    private void addAnnotationSpecificErrorResponses(Operation operation, HandlerMethod handlerMethod) {
        // ApiDocs 어노테이션에서 errorCodes 추출
        ApiDocs apiDocs = handlerMethod.getMethodAnnotation(ApiDocs.class);
        if (apiDocs != null && apiDocs.errorCodes().length > 0) {
            ApiResponses responses = operation.getResponses();
            
            for (String errorCode : apiDocs.errorCodes()) {
                Map<String, Object> errorExample = responseGenerator.generateSpecificErrorExample(errorCode);
                
                // HTTP 상태 코드 추정 (에러 코드 기반)
                String httpStatus = estimateHttpStatusFromErrorCode(errorCode);
                
                responses.addApiResponse(httpStatus, createErrorResponse(
                    "에러: " + errorCode,
                    errorExample
                ));
            }
        }
    }

    private String estimateHttpStatusFromErrorCode(String errorCode) {
        if (errorCode.contains("NOT_FOUND")) return "404";
        if (errorCode.contains("INSUFFICIENT_BALANCE")) return "402";
        if (errorCode.contains("UNAUTHORIZED")) return "403";
        if (errorCode.contains("ALREADY_") || errorCode.contains("OUT_OF_STOCK")) return "409";
        if (errorCode.contains("EXPIRED") || errorCode.contains("NOT_YET_STARTED")) return "410";
        return "400"; // 기본값
    }

    private ApiResponse createErrorResponse(String description, Map<String, Object> exampleData) {
        ApiResponse response = new ApiResponse();
        response.setDescription(description);

        // Content 설정
        Content content = new Content();
        MediaType mediaType = new MediaType();
        
        // Example 추가
        Example example = new Example();
        example.setValue(exampleData);
        mediaType.addExamples("default", example);
        
        content.addMediaType("application/json", mediaType);
        response.setContent(content);

        return response;
    }
}