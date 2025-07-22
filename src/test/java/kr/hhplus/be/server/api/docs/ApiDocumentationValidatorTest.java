package kr.hhplus.be.server.api.docs;

import kr.hhplus.be.server.api.docs.config.ApiDocumentationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 문서화 검증 테스트
 * 문서화 품질을 보장하기 위한 자동 검증
 */
@SpringBootTest
public class ApiDocumentationValidatorTest {
    
    @Autowired
    private ApiDocumentationValidator validator;
    
    @Test
    void validateAllEndpointsDocumented() {
        assertDoesNotThrow(() -> validator.validateDocumentation(), 
            "All endpoints must have proper documentation annotations");
    }
    
    @Test
    void validateDtosDocumented() {
        assertTrue(validator.allDtosDocumented(), 
            "All DTOs must implement DocumentedDto interface");
    }
    
    @Test
    void validateEndpointsHaveDocumentation() {
        assertTrue(validator.allEndpointsDocumented(), 
            "All controller endpoints must have @ApiDocs or domain-specific documentation");
    }
}