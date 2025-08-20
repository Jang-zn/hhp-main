package kr.hhplus.be.server.unit.service.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.service.ProductService;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ProductService.getPopularProductList 메서드 테스트
 */
@DisplayName("인기 상품 목록 조회 서비스")
class GetPopularProductListTest {

    @Mock
    private GetProductUseCase getProductUseCase;
    
    @Mock
    private GetPopularProductListUseCase getPopularProductListUseCase;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    private ProductService productService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(getProductUseCase, getPopularProductListUseCase, cachePort, keyGenerator);
    }

    @Test
    @DisplayName("정상적인 인기 상품 목록 조회가 성공한다 (Redis 랭킹 없음)")
    void getPopularProductList_Success() {
        // given
        int period = 7;
        int limit = 10;
        int offset = 0;
        
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("Popular Product 1").build(),
            TestBuilder.ProductBuilder.defaultProduct().name("Popular Product 2").build()
        );
        
        String dailyRankingKey = "ranking:2024-08-20";
        String cacheKey = "popular_products_7";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(List.of()); // Redis 랭킹 없음
        when(keyGenerator.generatePopularProductListCacheKey(period, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getPopularProductListUseCase.execute(period, limit, offset)).thenReturn(expectedProducts);
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Popular Product 1");
        assertThat(result.get(1).getName()).isEqualTo("Popular Product 2");
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(keyGenerator).generatePopularProductListCacheKey(period, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(expectedProducts), anyInt());
        verify(getPopularProductListUseCase).execute(period, limit, offset);
    }
    
    @Test
    @DisplayName("빈 인기 상품 목록 조회가 성공한다")
    void getPopularProductList_EmptyList() {
        // given
        int period = 7;
        int limit = 10;
        int offset = 0;
        
        String dailyRankingKey = "ranking:2024-08-20";
        String cacheKey = "popular_products_7";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(List.of()); // Redis 랭킹 없음
        when(keyGenerator.generatePopularProductListCacheKey(period, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getPopularProductListUseCase.execute(period, limit, offset)).thenReturn(List.of());
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(keyGenerator).generatePopularProductListCacheKey(period, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(List.of()), anyInt());
        verify(getPopularProductListUseCase).execute(period, limit, offset);
    }
    
    @Test
    @DisplayName("페이징 처리가 정상적으로 동작한다")
    void getPopularProductList_WithPaging() {
        // given
        int period = 14; // 다른 기간으로 테스트
        int limit = 5;
        int offset = 10;
        
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("Popular Product 11").build()
        );
        
        String dailyRankingKey = "ranking:2024-08-20";
        String cacheKey = "popular_products_14";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(List.of()); // Redis 랭킹 없음
        when(keyGenerator.generatePopularProductListCacheKey(period, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getPopularProductListUseCase.execute(period, limit, offset)).thenReturn(expectedProducts);
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(keyGenerator).generatePopularProductListCacheKey(period, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(expectedProducts), anyInt());
        verify(getPopularProductListUseCase).execute(period, limit, offset);
    }
    
    @Test
    @DisplayName("Redis 랭킹에서 인기 상품 조회가 성공한다")
    void getPopularProductList_FromRedisRanking_Success() {
        // given
        int period = 7;
        int limit = 5;
        int offset = 0;
        
        List<Long> rankedProductIds = List.of(101L, 102L, 103L);
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().id(101L).name("Ranked Product 1").build(),
            TestBuilder.ProductBuilder.defaultProduct().id(102L).name("Ranked Product 2").build(),
            TestBuilder.ProductBuilder.defaultProduct().id(103L).name("Ranked Product 3").build()
        );
        
        String dailyRankingKey = "ranking:2024-08-20";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(rankedProductIds);
        
        // 각 상품 개별 조회 mock
        when(keyGenerator.generateProductCacheKey(101L)).thenReturn("product:101");
        when(keyGenerator.generateProductCacheKey(102L)).thenReturn("product:102");
        when(keyGenerator.generateProductCacheKey(103L)).thenReturn("product:103");
        when(cachePort.get("product:101", Product.class)).thenReturn(expectedProducts.get(0));
        when(cachePort.get("product:102", Product.class)).thenReturn(expectedProducts.get(1));
        when(cachePort.get("product:103", Product.class)).thenReturn(expectedProducts.get(2));
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("Ranked Product 1");
        assertThat(result.get(1).getName()).isEqualTo("Ranked Product 2");
        assertThat(result.get(2).getName()).isEqualTo("Ranked Product 3");
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(cachePort, times(3)).get(anyString(), eq(Product.class));
        
        // Redis 랭킹 사용 시 기존 캐시 로직은 호출되지 않아야 함
        verify(keyGenerator, never()).generatePopularProductListCacheKey(anyInt(), anyInt(), anyInt());
        verify(getPopularProductListUseCase, never()).execute(anyInt(), anyInt(), anyInt());
    }
    
    @Test
    @DisplayName("Redis 랭킹이 비어있을 때 DB 폴백이 동작한다")
    void getPopularProductList_EmptyRanking_FallbackToDB() {
        // given
        int period = 7;
        int limit = 10;
        int offset = 0;
        
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("DB Fallback Product").build()
        );
        
        String dailyRankingKey = "ranking:2024-08-20";
        String cacheKey = "popular_products_7";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(List.of()); // 빈 랭킹
        when(keyGenerator.generatePopularProductListCacheKey(period, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getPopularProductListUseCase.execute(period, limit, offset)).thenReturn(expectedProducts);
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("DB Fallback Product");
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(keyGenerator).generatePopularProductListCacheKey(period, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(expectedProducts), anyInt());
        verify(getPopularProductListUseCase).execute(period, limit, offset);
    }
    
    @Test
    @DisplayName("Redis 랭킹에서 일부 상품 정보가 없을 때 필터링된다")
    void getPopularProductList_RankingWithMissingProducts() {
        // given
        int period = 7;
        int limit = 5;
        int offset = 0;
        
        List<Long> rankedProductIds = List.of(101L, 102L, 103L); // 3개 상품 ID
        List<Product> availableProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().id(101L).name("Available Product 1").build(),
            // 102L은 캐시에 없음
            TestBuilder.ProductBuilder.defaultProduct().id(103L).name("Available Product 3").build()
        );
        
        String dailyRankingKey = "ranking:2024-08-20";
        
        when(keyGenerator.generateDailyRankingKey(anyString())).thenReturn(dailyRankingKey);
        when(cachePort.getProductRanking(dailyRankingKey, offset, limit)).thenReturn(rankedProductIds);
        
        // 상품 개별 조회 mock - 102L은 없음
        when(keyGenerator.generateProductCacheKey(101L)).thenReturn("product:101");
        when(keyGenerator.generateProductCacheKey(102L)).thenReturn("product:102");
        when(keyGenerator.generateProductCacheKey(103L)).thenReturn("product:103");
        when(cachePort.get("product:101", Product.class)).thenReturn(availableProducts.get(0));
        when(cachePort.get("product:102", Product.class)).thenReturn(null); // 없음
        when(cachePort.get("product:103", Product.class)).thenReturn(availableProducts.get(1));
        
        // when
        List<Product> result = productService.getPopularProductList(period, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2); // 102L 제외하고 2개만
        assertThat(result.get(0).getName()).isEqualTo("Available Product 1");
        assertThat(result.get(1).getName()).isEqualTo("Available Product 3");
        
        verify(keyGenerator).generateDailyRankingKey(anyString());
        verify(cachePort).getProductRanking(dailyRankingKey, offset, limit);
        verify(cachePort, times(3)).get(anyString(), eq(Product.class));
    }
}