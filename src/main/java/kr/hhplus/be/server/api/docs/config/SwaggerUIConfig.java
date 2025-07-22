package kr.hhplus.be.server.api.docs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Swagger UI 설정 커스터마이저
 * UI 테마 및 추가 정보 설정
 * 여기가 swagger 들어가면 흰화면 아래쪽에 뜨는 추가정보
 */
@Component
public class SwaggerUIConfig implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        // // API 정보 확장
        // openApi.getInfo()
        //         .contact(new Contact()
        //                 .name("항해플러스 개발팀")
        //                 .email("dev@hhplus.kr")
        //                 .url("https://hhplus.kr"))
        //         .license(new License()
        //                 .name("MIT License")
        //                 .url("https://opensource.org/licenses/MIT"))
        //         .version("1.0.0");

        // 태그 정렬 및 설명 추가
        openApi.getTags().forEach(tag -> {
            switch (tag.getName()) {
                case "잔액 관리" -> tag.setDescription("사용자 잔액 충전, 조회 및 관리 기능");
                case "상품 관리" -> tag.setDescription("상품 목록 조회 및 인기 상품 조회 기능");
                case "쿠폰 관리" -> tag.setDescription("쿠폰 발급 및 사용자 보유 쿠폰 조회 기능");
                case "주문/결제 관리" -> tag.setDescription("주문 생성, 결제 처리 및 주문 조회 기능");
            }
        });
    }
}