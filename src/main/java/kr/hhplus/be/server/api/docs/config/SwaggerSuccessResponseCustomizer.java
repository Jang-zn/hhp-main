package kr.hhplus.be.server.api.docs.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import kr.hhplus.be.server.api.docs.annotation.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swagger 성공 응답 커스터마이저
 * 성공 응답의 예시 데이터를 자동으로 생성
 */
@Component
public class SwaggerSuccessResponseCustomizer implements OperationCustomizer {

    private final ApiResponseGenerator responseGenerator;

    public SwaggerSuccessResponseCustomizer(ApiResponseGenerator responseGenerator) {
        this.responseGenerator = responseGenerator;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        addSuccessResponseExamples(operation, handlerMethod);
        return operation;
    }

    private void addSuccessResponseExamples(Operation operation, HandlerMethod handlerMethod) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        // 메서드 반환 타입 기반으로 성공 응답 예시 생성
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        Object exampleData = generateExampleData(returnType);
        
        // HTTP 메서드에 따른 상태 코드 결정
        String httpMethod = extractHttpMethod(handlerMethod);
        String statusCode = "201".equals(httpMethod) ? "201" : "200";
        
        // 성공 응답 추가
        if (!responses.containsKey(statusCode)) {
            Map<String, Object> successResponse = responseGenerator.generateSuccessResponse(exampleData);
            responses.addApiResponse(statusCode, createSuccessResponse(
                "201".equals(statusCode) ? "생성 성공" : "요청 성공",
                successResponse
            ));
        }
    }

    private String extractHttpMethod(HandlerMethod handlerMethod) {
        // POST 메서드인지 확인 (201 응답용)
        if (handlerMethod.hasMethodAnnotation(org.springframework.web.bind.annotation.PostMapping.class)) {
            return "201";
        }
        return "200";
    }

    private Object generateExampleData(Class<?> returnType) {
        // List 타입 처리
        if (List.class.isAssignableFrom(returnType)) {
            return List.of(generateSingleExampleData(Object.class));
        }
        
        return generateSingleExampleData(returnType);
    }

    private Object generateSingleExampleData(Class<?> type) {
        // 기본 타입들
        if (type == String.class) return "example";
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == BigDecimal.class) return new BigDecimal("10000");
        if (type == LocalDateTime.class) return LocalDateTime.now().toString();
        if (type == Boolean.class || type == boolean.class) return true;

        // DTO 타입들 - DocumentedDto 인터페이스 구현 여부 확인
        if (DocumentedDto.class.isAssignableFrom(type)) {
            try {
                DocumentedDto instance = (DocumentedDto) type.getDeclaredConstructor().newInstance();
                Map<String, Object> exampleData = new HashMap<>();
                
                instance.getFieldDocumentation().forEach((fieldName, schemaInfo) -> {
                    exampleData.put(fieldName, parseExampleValue(schemaInfo.example()));
                });
                
                return exampleData;
            } catch (Exception e) {
                // 인스턴스 생성 실패시 기본 예시 데이터 반환
                return generateDefaultExampleByClassName(type.getSimpleName());
            }
        }

        // 클래스명 기반 기본 예시 생성
        return generateDefaultExampleByClassName(type.getSimpleName());
    }

    private Object parseExampleValue(String example) {
        if (example == null) return null;
        
        // 숫자 형태 확인
        try {
            if (example.contains(".")) {
                return new BigDecimal(example);
            } else {
                return Long.parseLong(example);
            }
        } catch (NumberFormatException e) {
            // 문자열 그대로 반환
            return example;
        }
    }

    private Object generateDefaultExampleByClassName(String className) {
        return switch (className.toLowerCase()) {
            case "balanceresponse" -> Map.of(
                "userId", 1L,
                "amount", new BigDecimal("50000"),
                "updatedAt", LocalDateTime.now().toString()
            );
            case "couponresponse" -> Map.of(
                "couponId", 1L,
                "code", "SUMMER2025",
                "discountRate", new BigDecimal("0.10"),
                "validUntil", LocalDateTime.now().plusDays(30).toString()
            );
            case "productresponse" -> Map.of(
                "productId", 1L,
                "name", "상품 A",
                "price", new BigDecimal("10000"),
                "stock", 100
            );
            case "orderresponse" -> Map.of(
                "orderId", 1L,
                "userId", 1L,
                "status", "PENDING",
                "totalAmount", new BigDecimal("25000"),
                "createdAt", LocalDateTime.now().toString(),
                "items", List.of()
            );
            case "paymentresponse" -> Map.of(
                "paymentId", 1L,
                "orderId", 1L,
                "status", "COMPLETED",
                "finalAmount", new BigDecimal("23000"),
                "paidAt", LocalDateTime.now().toString()
            );
            default -> Map.of("message", "Success");
        };
    }

    private ApiResponse createSuccessResponse(String description, Map<String, Object> exampleData) {
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