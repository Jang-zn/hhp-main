package kr.hhplus.be.server.api.docs.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 중앙화 설정
 * 기존 config/SwaggerConfig.java를 대체하여 docs 패키지로 이동
 * 에러 응답 자동 생성 및 커스터마이징 포함
 */
@OpenAPIDefinition(
        info = @Info(
                title = "E-Commerce API",
                version = "1.0.0",
                description = "항해플러스 이커머스 서비스 API 문서\n\n" +
                        "## 주요 기능\n" +
                        "- 잔액 충전 및 조회\n" +
                        "- 상품 목록 및 인기 상품 조회\n" +
                        "- 쿠폰 발급 및 조회\n" +
                        "- 주문 생성 및 결제 처리\n\n" +
                        "## 에러 코드 체계\n" +
                        "### HTTP 상태 코드\n" +
                        "- **200**: 성공\n" +
                        "- **201**: 생성 성공\n" +
                        "- **400**: 잘못된 요청 (입력값 오류)\n" +
                        "- **402**: 잔액 부족\n" +
                        "- **403**: 권한 없음 (접근 제한)\n" +
                        "- **404**: 리소스 없음\n" +
                        "- **409**: 충돌 (이미 존재, 재고 부족)\n" +
                        "- **410**: 만료/비활성화\n" +
                        "- **500**: 서버 내부 오류\n\n" +
                        "### 도메인별 에러 코드\n" +
                        "- **Balance**: `INSUFFICIENT_BALANCE`, `BALANCE_NOT_FOUND`, `INVALID_AMOUNT`\n" +
                        "- **Coupon**: `COUPON_NOT_FOUND`, `COUPON_ALREADY_ISSUED`, `COUPON_EXPIRED`\n" +
                        "- **Product**: `PRODUCT_NOT_FOUND`, `PRODUCT_OUT_OF_STOCK`\n" +
                        "- **Order**: `ORDER_NOT_FOUND`, `ORDER_UNAUTHORIZED`, `ORDER_ALREADY_PAID`\n\n" +
                        "모든 에러 응답은 표준화된 형식으로 제공되며, 각 엔드포인트별로 발생 가능한 에러의 예시를 확인할 수 있습니다."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬 개발 서버")
        }
)
@Configuration
public class SwaggerConfig {

    private final SwaggerResponseCustomizer responseCustomizer;
    private final SwaggerSuccessResponseCustomizer successResponseCustomizer;
    private final SwaggerUIConfig swaggerUIConfig;

    public SwaggerConfig(SwaggerResponseCustomizer responseCustomizer, 
                        SwaggerSuccessResponseCustomizer successResponseCustomizer,
                        SwaggerUIConfig swaggerUIConfig) {
        this.responseCustomizer = responseCustomizer;
        this.successResponseCustomizer = successResponseCustomizer;
        this.swaggerUIConfig = swaggerUIConfig;
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("v1")
                .packagesToScan("kr.hhplus.be.server.api.controller")
                .addOperationCustomizer(responseCustomizer)
                .addOperationCustomizer(successResponseCustomizer)
                .addOpenApiCustomizer(swaggerUIConfig)
                .build();
    }
}