package kr.hhplus.be.server.unit.service.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.service.ProductService;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.CreateProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.UpdateProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.DeleteProductUseCase;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ProductService.getProductList 메서드 테스트
 */
@DisplayName("상품 목록 조회 서비스")
class GetProductListTest {

    @Mock
    private GetProductUseCase getProductUseCase;
    
    @Mock
    private GetPopularProductListUseCase getPopularProductListUseCase;
    
    @Mock
    private CreateProductUseCase createProductUseCase;
    
    @Mock
    private UpdateProductUseCase updateProductUseCase;
    
    @Mock
    private DeleteProductUseCase deleteProductUseCase;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    private ProductService productService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(getProductUseCase, getPopularProductListUseCase, createProductUseCase, updateProductUseCase, deleteProductUseCase, eventPublisher);
    }

    @Test
    @DisplayName("정상적인 상품 목록 조회가 성공한다")
    void getProductList_Success() {
        // given
        int limit = 10;
        int offset = 0;
        
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("Product 1").build(),
            TestBuilder.ProductBuilder.defaultProduct().name("Product 2").build()
        );
        
        String cacheKey = "product_list_10_0";
        when(keyGenerator.generateProductListCacheKey(limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getProductUseCase.execute(limit, offset)).thenReturn(expectedProducts);
        
        // when
        List<Product> result = productService.getProductList(limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Product 1");
        assertThat(result.get(1).getName()).isEqualTo("Product 2");
        
        verify(getProductUseCase).execute(limit, offset);
    }
    
    @Test
    @DisplayName("빈 상품 목록 조회가 성공한다")
    void getProductList_EmptyList() {
        // given
        int limit = 10;
        int offset = 0;
        
        String cacheKey = "product_list_10_0";
        when(keyGenerator.generateProductListCacheKey(limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getProductUseCase.execute(limit, offset)).thenReturn(List.of());
        
        // when
        List<Product> result = productService.getProductList(limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(getProductUseCase).execute(limit, offset);
    }
    
    @Test
    @DisplayName("페이징 처리가 정상적으로 동작한다")
    void getProductList_WithPaging() {
        // given
        int limit = 5;
        int offset = 10;
        
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("Product 11").build()
        );
        
        String cacheKey = "product_list_5_10";
        when(keyGenerator.generateProductListCacheKey(limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getProductUseCase.execute(limit, offset)).thenReturn(expectedProducts);
        
        // when
        List<Product> result = productService.getProductList(limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        verify(getProductUseCase).execute(limit, offset);
    }
}