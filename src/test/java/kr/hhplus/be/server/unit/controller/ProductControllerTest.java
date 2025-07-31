package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.facade.product.GetProductListFacade;
import kr.hhplus.be.server.domain.facade.product.GetPopularProductListFacade;
import kr.hhplus.be.server.domain.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProductController 단위 테스트")
class ProductControllerTest {

    @Mock
    private GetProductListFacade getProductListFacade;
    @Mock
    private GetPopularProductListFacade getPopularProductListFacade;
    
    private ProductController productController;
    
    private Product testProduct;
    private List<Product> testProducts;
    private ProductRequest testRequest;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productController = new ProductController(getProductListFacade, getPopularProductListFacade);
        
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("50000"))
            .stock(100)
            .build();
            
        testProducts = List.of(testProduct);
        testRequest = new ProductRequest(10, 0);
    }

    @Test
    @DisplayName("성공 - 정상 상품 목록 조회")
    void getProductList_Success() {
        // given
        when(getProductListFacade.getProductList(10, 0)).thenReturn(testProducts);
        
        // when
        List<ProductResponse> result = productController.getProductList(testRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("Test Product");
        assertThat(result.get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(result.get(0).stock()).isEqualTo(100);
        verify(getProductListFacade).getProductList(10, 0);
    }
    
    @Test
    @DisplayName("실패 - null 요청")
    void getProductList_NullRequest() {
        // given
        ProductRequest nullRequest = null;
        
        // when & then
        assertThatThrownBy(() -> productController.getProductList(nullRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
            
        verify(getProductListFacade, never()).getProductList(anyInt(), anyInt());
    }
    
    @Test
    @DisplayName("성공 - 정상 인기 상품 조회")
    void getPopularProducts_Success() {
        // given
        ProductRequest popularRequest = new ProductRequest(7);
        when(getPopularProductListFacade.getPopularProductList(7)).thenReturn(testProducts);
        
        // when
        List<ProductResponse> result = productController.getPopularProducts(popularRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("Test Product");
        assertThat(result.get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(result.get(0).stock()).isEqualTo(100);
        
        verify(getPopularProductListFacade).getPopularProductList(7);
    }
}