package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.facade.product.GetProductListFacade;
import kr.hhplus.be.server.domain.facade.product.GetPopularProductListFacade;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.util.TestBuilder;
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

/**
 * ProductController 비즈니스 시나리오 테스트
 * 
 * Why: 상품 컴트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하는지 검증
 * How: 상품 조회 시나리오를 반영한 컴트롤러 레이어 테스트로 구성
 */
@DisplayName("상품 컴트롤러 API 비즈니스 시나리오")
class ProductControllerTest {

    @Mock
    private GetProductListFacade getProductListFacade;
    @Mock
    private GetPopularProductListFacade getPopularProductListFacade;
    
    private ProductController productController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productController = new ProductController(getProductListFacade, getPopularProductListFacade);
    }

    @Test
    @DisplayName("고객이 상품 목록을 성공적으로 조회한다")
    void getProductList_Success() {
        // given - 고객이 상품 목록을 페이지별로 조회하는 상황
        ProductRequest pageRequest = new ProductRequest(10, 0);
        Product availableProduct = TestBuilder.ProductBuilder.defaultProduct()
                .id(1L)
                .name("인기상품")
                .price(new BigDecimal("50000"))
                .stock(100)
                .build();
        List<Product> availableProducts = List.of(availableProduct);
        
        when(getProductListFacade.getProductList(10, 0)).thenReturn(availableProducts);
        
        // when
        List<ProductResponse> result = productController.getProductList(pageRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("인기상품");
        assertThat(result.get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(result.get(0).stock()).isEqualTo(100);
        verify(getProductListFacade).getProductList(10, 0);
    }
    
    @Test
    @DisplayName("잘못된 요청 형식으로 상품 목록 조회 시 예외가 발생한다")
    void getProductList_NullRequest() {
        // given - 잘못된 API 요청 상황
        ProductRequest invalidRequest = null;
        
        // when & then
        assertThatThrownBy(() -> productController.getProductList(invalidRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
            
        verify(getProductListFacade, never()).getProductList(anyInt(), anyInt());
    }
    
    @Test
    @DisplayName("고객이 인기 상품 목록을 성공적으로 조회한다")
    void getPopularProducts_Success() {
        // given - 고객이 인기 상품을 확인하는 상황
        ProductRequest popularRequest = new ProductRequest(7);
        Product popularProduct = TestBuilder.ProductBuilder.popularProduct()
                .id(1L)
                .name("베스트셀러 제품")
                .price(new BigDecimal("50000"))
                .stock(100)
                .build();
        List<Product> popularProducts = List.of(popularProduct);
        
        when(getPopularProductListFacade.getPopularProductList(7)).thenReturn(popularProducts);
        
        // when
        List<ProductResponse> result = productController.getPopularProducts(popularRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("베스트셀러 제품");
        assertThat(result.get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(result.get(0).stock()).isEqualTo(100);
        
        verify(getPopularProductListFacade).getPopularProductList(7);
    }

    @Test
    @DisplayName("빈 상품 목록에 대해 빈 응답을 반환한다")
    void getProductList_EmptyResult() {
        // given - 상품이 없는 상황 (품절 또는 신규 서비스)
        ProductRequest emptyRequest = new ProductRequest(10, 0);
        List<Product> emptyProducts = List.of();
        
        when(getProductListFacade.getProductList(10, 0)).thenReturn(emptyProducts);
        
        // when
        List<ProductResponse> result = productController.getProductList(emptyRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(getProductListFacade).getProductList(10, 0);
    }

    @Test
    @DisplayName("인기 상품 요청에 대해 null 요청 시 예외가 발생한다")
    void getPopularProducts_NullRequest() {
        // given - 잘못된 인기 상품 API 요청
        ProductRequest invalidRequest = null;
        
        // when & then
        assertThatThrownBy(() -> productController.getPopularProducts(invalidRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
            
        verify(getPopularProductListFacade, never()).getPopularProductList(anyInt());
    }
}