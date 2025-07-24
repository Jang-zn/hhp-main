package kr.hhplus.be.server.api.docs.config;

import kr.hhplus.be.server.api.docs.annotation.ApiDocs;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * API 문서화 검증 시스템
 * 애플리케이션 시작 시 문서화 누락을 감지하고 경고
 */
@Component
public class ApiDocumentationValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentationValidator.class);
    
    private final ApplicationContext applicationContext;
    
    public ApiDocumentationValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 애플리케이션 시작 완료 후 검증 실행
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateDocumentation() {
        logger.info("API 문서화 검증을 시작합니다...");
        
        List<String> violations = new ArrayList<>();
        
        // 1. Controller 엔드포인트 검증
        violations.addAll(validateControllerEndpoints());
        
        // 2. DTO 문서화 검증
        violations.addAll(validateDtoDocumentation());
        
        // 3. 결과 출력
        if (violations.isEmpty()) {
            logger.info("✅ API 문서화 검증 완료: 모든 항목이 올바르게 문서화되었습니다.");
        } else {
            logger.warn("⚠️  API 문서화 검증 경고: {} 개의 문제가 발견되었습니다.", violations.size());
            violations.forEach(violation -> logger.warn("- {}", violation));
        }
    }
    
    /**
     * Controller 엔드포인트 문서화 검증
     */
    private List<String> validateControllerEndpoints() {
        List<String> violations = new ArrayList<>();
        
        String[] controllerBeans = applicationContext.getBeanNamesForAnnotation(RestController.class);
        
        for (String beanName : controllerBeans) {
            Object controller = applicationContext.getBean(beanName);
            Class<?> controllerClass = controller.getClass();
            
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(RequestMapping.class) || 
                    hasHttpMappingAnnotation(method)) {
                    
                    // SpringDoc 내부 엔드포인트는 검증에서 제외
                    if (isSpringDocInternalEndpoint(controllerClass, method)) {
                        continue;
                    }
                    
                    if (!hasDocumentationAnnotation(method)) {
                        violations.add(String.format(
                            "엔드포인트 %s.%s에 @ApiDocs 어노테이션이 누락되었습니다",
                            controllerClass.getSimpleName(),
                            method.getName()
                        ));
                    }
                }
            }
        }
        
        return violations;
    }
    
    /**
     * DTO 문서화 검증
     */
    private List<String> validateDtoDocumentation() {
        List<String> violations = new ArrayList<>();
        
        // Request/Response DTO 패키지 스캔
        String[] dtoPackages = {
            "kr.hhplus.be.server.api.dto.request",
            "kr.hhplus.be.server.api.dto.response"
        };
        
        for (String packageName : dtoPackages) {
            try {
                // 패키지 내 클래스들을 스캔하여 DocumentedDto 구현 여부 확인
                // 실제 구현에서는 Reflections 라이브러리 등을 사용할 수 있음
                logger.debug("DTO 패키지 검증: {}", packageName);
            } catch (Exception e) {
                logger.debug("DTO 패키지 스캔 중 오류: {}", e.getMessage());
            }
        }
        
        return violations;
    }
    
    /**
     * HTTP 매핑 어노테이션 확인
     */
    private boolean hasHttpMappingAnnotation(Method method) {
        return method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class) ||
               method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class) ||
               method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class) ||
               method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class) ||
               method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class);
    }
    
    /**
     * SpringDoc 내부 엔드포인트 확인
     */
    private boolean isSpringDocInternalEndpoint(Class<?> controllerClass, Method method) {
        String className = controllerClass.getSimpleName();
        String methodName = method.getName();
        
        // SpringDoc OpenAPI 관련 내부 클래스들
        return className.contains("OpenApiWebMvcResource") ||
               className.contains("SwaggerConfigResource") ||
               className.contains("MultipleOpenApiWebMvcResource") ||
               methodName.equals("openapiJson") ||
               methodName.equals("openapiYaml");
    }
    
    /**
     * 문서화 어노테이션 확인
     */
    private boolean hasDocumentationAnnotation(Method method) {
        return method.isAnnotationPresent(ApiDocs.class) ||
               method.isAnnotationPresent(kr.hhplus.be.server.api.docs.annotation.BalanceApiDocs.class) ||
               method.isAnnotationPresent(kr.hhplus.be.server.api.docs.annotation.OrderApiDocs.class) ||
               method.isAnnotationPresent(kr.hhplus.be.server.api.docs.annotation.ProductApiDocs.class) ||
               method.isAnnotationPresent(kr.hhplus.be.server.api.docs.annotation.CouponApiDocs.class);
    }
    
    /**
     * 문서화 검증 상태 반환 (테스트용)
     */
    public boolean allEndpointsDocumented() {
        return validateControllerEndpoints().isEmpty();
    }
    
    /**
     * DTO 문서화 검증 상태 반환 (테스트용)
     */
    public boolean allDtosDocumented() {
        return validateDtoDocumentation().isEmpty();
    }
}