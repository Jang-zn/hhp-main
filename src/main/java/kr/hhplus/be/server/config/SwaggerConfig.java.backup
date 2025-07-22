package kr.hhplus.be.server.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "E-Commerce API",
                version = "1.0.0",
                description = "항해플러스 이커머스 서비스 API 문서\n\n" +
                        "## 주요 기능\n" +
                        "- 잔액 충전 및 조회\n" +
                        "- 상품 목록 및 인기 상품 조회\n" +
                        "- 쿠폰 발급 및 조회\n" +
                        "- 주문 생성 및 결제 처리"
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬 개발 서버"),
                @Server(url = "https://api.hhplus.kr", description = "운영 서버")
        }
)
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("v1")
                .packagesToScan("kr.hhplus.be.server.api.controller")
                .build();
    }
} 