package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 API 통합 테스트
 * 
 * Why: 상품 조회 API의 전체 플로우가 비즈니스 요구사항을 만족하는지 검증
 * How: 실제 고객의 상품 조회 시나리오를 반영한 API 레벨 테스트
 */
@DisplayName("상품 API 통합 시나리오")
public class ProductTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepositoryPort productRepositoryPort;

    // === 상품 목록 조회 시나리오 ===

    @Test
    @DisplayName("고객이 기본 설정으로 전체 상품 목록을 조회할 수 있다")
    void customerCanViewAllProductsWithDefaultSettings() throws Exception {
        // Given
        setupUniqueTestProducts();
        
        // When & Then
        mockMvc.perform(get("/api/product/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(6)));
    }

    @Test
    @DisplayName("고객이 페이징을 사용하여 상품 목록을 부분 조회할 수 있다")
    void customerCanViewProductsWithPagination() throws Exception {
        // Given
        setupUniqueTestProducts();
        
        // When & Then - 2개씩, 1페이지 건너뛰고 조회
        mockMvc.perform(get("/api/product/list")
                        .param("limit", "2")
                        .param("offset", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("잘못된 limit 파라미터로 상품 조회 시 입력 오류가 발생한다")
    void preventsProductListQueryWithInvalidLimit() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/product/list")
                        .param("limit", "-1")
                        .param("offset", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("잘못된 offset 파라미터로 상품 조회 시 입력 오류가 발생한다")
    void preventsProductListQueryWithInvalidOffset() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/product/list")
                        .param("limit", "10")
                        .param("offset", "-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.message").exists());
    }
    
    // === 인기 상품 조회 시나리오 ===

    @Test
    @DisplayName("고객이 지난 3일간의 인기 상품 목록을 조회할 수 있다")
    void customerCanViewPopularProductsFromLast3Days() throws Exception {
        // Given
        setupUniqueTestProducts();
        // NOTE: 현재 구현에서는 인기도 로직이 단순 조회일 수 있음
        // 추후 주문 데이터 기반 인기도 로직 구현 시 테스트 확장 필요

        // When & Then
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("잘못된 기간으로 인기 상품 조회 시 입력 오류가 발생한다")
    void preventsPopularProductQueryWithInvalidDays() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.message").exists());
    }
    
    // === 헬퍼 메서드 ===
    
    private void setupUniqueTestProducts() {
        String suffix = "_" + System.nanoTime();
        
        // IT 기기 카테고리
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("노트북" + suffix).price(new BigDecimal("1500000")).stock(10).build());
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("스마트폰" + suffix).price(new BigDecimal("1200000")).stock(20).build());
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("태블릿" + suffix).price(new BigDecimal("800000")).stock(15).build());
        
        // 주변기기 카테고리
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("무선이어폰" + suffix).price(new BigDecimal("250000")).stock(50).build());
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("키보드" + suffix).price(new BigDecimal("120000")).stock(30).build());
        productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("마우스" + suffix).price(new BigDecimal("50000")).stock(100).build());
    }
}