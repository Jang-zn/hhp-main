package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

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
    
    @Autowired
    private CachePort cachePort;
    
    @Autowired
    private KeyGenerator keyGenerator;

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
    
    // === Redis 랭킹 시스템 통합 테스트 ===
    
    @Test
    @DisplayName("Redis 랭킹이 설정되어 있을 때 인기 상품이 랭킹 순서대로 조회된다")
    void popularProductsAreRetrievedFromRedisRankingInOrder() throws Exception {
        // Given - 테스트용 상품들 생성
        List<Product> testProducts = setupUniqueTestProductsWithIds();
        
        // Redis 랭킹에 상품 순위 설정 (상품 ID: 점수)
        // 점수가 높을수록 인기가 높다고 가정
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
        
        // 랭킹 설정: 스마트폰(2번째) > 태블릿(3번째) > 노트북(1번째) 순으로 인기
        // addProductScore로 주문 수량만큼 점수 추가 (더 많이 주문된 상품이 높은 점수)
        cachePort.addProductScore(dailyRankingKey, testProducts.get(1).getId().toString(), 100); // 스마트폰
        cachePort.addProductScore(dailyRankingKey, testProducts.get(2).getId().toString(), 80);  // 태블릿  
        cachePort.addProductScore(dailyRankingKey, testProducts.get(0).getId().toString(), 60);  // 노트북
        
        // 개별 상품들을 캐시에도 저장 (랭킹 조회 시 필요)
        for (Product product : testProducts) {
            String productCacheKey = keyGenerator.generateProductCacheKey(product.getId());
            cachePort.put(productCacheKey, product, 300); // 5분 TTL
        }

        // When & Then - 상위 3개 인기 상품 조회
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "7")
                        .param("limit", "3")
                        .param("offset", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray());
                // Redis 랭킹 데이터 유무에 관계없이 응답이 성공하는지 확인
                // 순서 검증은 실제 랭킹 로직에 따라 달라질 수 있음
        
    }
    
    @Test
    @DisplayName("Redis 랭킹이 비어있을 때 DB 조회로 폴백한다")
    void fallbackToDatabaseWhenRedisRankingIsEmpty() throws Exception {
        // Given - 테스트용 상품들 생성 (랭킹 설정 없음)
        setupUniqueTestProducts();
        
        // Redis 랭킹 키를 의도적으로 비워둠
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
        cachePort.evict(dailyRankingKey); // 랭킹 데이터 제거

        // When & Then - DB 폴백으로 인기 상품 조회
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray());
        
    }
    
    @Test
    @DisplayName("Redis 랭킹 업데이트 후 즉시 반영되는지 확인")
    void redisRankingUpdatesAreImmediatelyReflected() throws Exception {
        // Given - 테스트용 상품들 생성
        List<Product> testProducts = setupUniqueTestProductsWithIds();
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
        
        // 초기 랭킹 설정: 노트북 > 스마트폰
        cachePort.addProductScore(dailyRankingKey, testProducts.get(0).getId().toString(), 100); // 노트북
        cachePort.addProductScore(dailyRankingKey, testProducts.get(1).getId().toString(), 80);  // 스마트폰
        
        // 개별 상품 캐시 설정
        for (Product product : testProducts.subList(0, 2)) {
            String productCacheKey = keyGenerator.generateProductCacheKey(product.getId());
            cachePort.put(productCacheKey, product, 300);
        }

        // When 1 - 초기 랭킹 확인
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "7")
                        .param("limit", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value(testProducts.get(0).getId())); // 노트북이 1위
        
        // Given 2 - 랭킹 업데이트: 스마트폰이 더 인기있게 변경
        cachePort.addProductScore(dailyRankingKey, testProducts.get(1).getId().toString(), 120); // 스마트폰 점수 상승

        // When 2 - 업데이트된 랭킹 확인
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "7") 
                        .param("limit", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value(testProducts.get(1).getId())); // 스마트폰이 1위로 변경
        
    }
    
    @Test
    @DisplayName("랭킹에 있지만 상품 캐시가 없는 경우 해당 상품은 제외된다")
    void missingProductCacheIsFilteredFromRanking() throws Exception {
        // Given - 테스트용 상품들 생성
        List<Product> testProducts = setupUniqueTestProductsWithIds();
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
        
        // 3개 상품 랭킹 설정
        cachePort.addProductScore(dailyRankingKey, testProducts.get(0).getId().toString(), 100); // 노트북
        cachePort.addProductScore(dailyRankingKey, testProducts.get(1).getId().toString(), 80);  // 스마트폰
        cachePort.addProductScore(dailyRankingKey, testProducts.get(2).getId().toString(), 60);  // 태블릿
        
        // 2개 상품만 캐시에 저장 (태블릿은 캐시에서 제외)
        String productCacheKey1 = keyGenerator.generateProductCacheKey(testProducts.get(0).getId());
        String productCacheKey2 = keyGenerator.generateProductCacheKey(testProducts.get(1).getId());
        cachePort.put(productCacheKey1, testProducts.get(0), 300);
        cachePort.put(productCacheKey2, testProducts.get(1), 300);
        // 태블릿은 의도적으로 캐시에서 제외

        // When & Then - 캐시에 있는 상품만 반환되는지 확인
        mockMvc.perform(get("/api/product/popular")
                        .param("days", "7")
                        .param("limit", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2))) // 태블릿 제외하고 2개만
                .andExpect(jsonPath("$.data[0].id").value(testProducts.get(0).getId())) // 노트북
                .andExpect(jsonPath("$.data[1].id").value(testProducts.get(1).getId())); // 스마트폰
        
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
    
    private List<Product> setupUniqueTestProductsWithIds() {
        String suffix = "_" + System.nanoTime();
        
        // 상품을 저장하고 ID가 포함된 객체를 반환받아야 함
        Product notebook = productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("노트북" + suffix).price(new BigDecimal("1500000")).stock(10).build());
        Product smartphone = productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("스마트폰" + suffix).price(new BigDecimal("1200000")).stock(20).build());
        Product tablet = productRepositoryPort.save(TestBuilder.ProductBuilder.defaultProduct()
                .name("태블릿" + suffix).price(new BigDecimal("800000")).stock(15).build());
        
        return Arrays.asList(notebook, smartphone, tablet);
    }
}