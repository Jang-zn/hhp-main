package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController 단위 테스트")
class ProductControllerTest {

    private ProductController productController;
    
    @Mock
    private GetProductUseCase getProductUseCase;

    @Mock
    private GetPopularProductListUseCase getPopularProductListUseCase;

    @BeforeEach
    void setUp() {
        productController = new ProductController(getProductUseCase, getPopularProductListUseCase);
    }


    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class GetProductsTests {
        
        private static Stream<Arguments> providePaginationData() {
            return Stream.of(
                    Arguments.of(5, 0), // 작은 페이지 크기
                    Arguments.of(10, 10), // 오프셋 적용
                    Arguments.of(20, 0), // 큰 페이지 크기
                    Arguments.of(1, 0) // 최소 페이지 크기
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 상품 목록 조회")
        void getProducts_Success() {
        // given
        ProductRequest request = new ProductRequest(10, 0);
        List<Product> mockProducts = Arrays.asList(
            Product.builder().id(1L).name("노트북").price(BigDecimal.valueOf(1000000)).stock(10).reservedStock(0).build(),
            Product.builder().id(2L).name("스마트폰").price(BigDecimal.valueOf(800000)).stock(5).reservedStock(0).build(),
            Product.builder().id(3L).name("태블릿").price(BigDecimal.valueOf(500000)).stock(15).reservedStock(0).build()
        );
        when(getProductUseCase.execute(10, 0)).thenReturn(mockProducts);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
        assertThat(response).hasSize(3);
        assertThat(response.get(0).name()).isEqualTo("노트북");
        assertThat(response.get(1).name()).isEqualTo("스마트폰");
        assertThat(response.get(2).name()).isEqualTo("태블릿");
    }

        @ParameterizedTest
        @MethodSource("providePaginationData")
        @DisplayName("성공케이스: 다양한 페이지네이션으로 상품 조회")
        void getProducts_WithDifferentPagination(int limit, int offset) {
        // given
        ProductRequest request = new ProductRequest(limit, offset);
        List<Product> mockProducts = Arrays.asList(
            Product.builder().id(1L).name("노트북").price(BigDecimal.valueOf(1000000)).stock(10).reservedStock(0).build(),
            Product.builder().id(2L).name("스마트폰").price(BigDecimal.valueOf(800000)).stock(5).reservedStock(0).build(),
            Product.builder().id(3L).name("태블릿").price(BigDecimal.valueOf(500000)).stock(15).reservedStock(0).build()
        );
        when(getProductUseCase.execute(limit, offset)).thenReturn(mockProducts);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
            assertThat(response).hasSize(3);
        }

        

        @Test
        @DisplayName("실패케이스: null 요청으로 상품 조회")
        void getProducts_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> productController.getProductList(null))
                    .isInstanceOf(CommonException.InvalidRequest.class)
                    .hasMessage(CommonException.Messages.REQUEST_CANNOT_BE_NULL);
        }

        @Test
        @DisplayName("실패케이스: 비정상 페이지네이션 파라미터")
        void getProducts_WithInvalidPagination() {
            // given
            ProductRequest invalidRequest = new ProductRequest(-1, -1);
            when(getProductUseCase.execute(-1, -1)).thenThrow(new CommonException.InvalidPagination());
            
            // when & then
            assertThatThrownBy(() -> productController.getProductList(invalidRequest))
                    .isInstanceOf(CommonException.InvalidPagination.class);
        }
    }

    @Nested
    @DisplayName("인기 상품 조회 테스트")
    class GetPopularProductsTests {
        
        @Test
        @DisplayName("성공케이스: 정상 인기 상품 조회")
        void getPopularProducts_Success() {
        // given
        ProductRequest request = new ProductRequest(3);
        List<Product> mockPopularProducts = Arrays.asList(
            Product.builder().id(2L).name("스마트폰").price(BigDecimal.valueOf(800000)).stock(5).reservedStock(0).build(),
            Product.builder().id(1L).name("노트북").price(BigDecimal.valueOf(1000000)).stock(10).reservedStock(0).build(),
            Product.builder().id(4L).name("무선이어폰").price(BigDecimal.valueOf(150000)).stock(20).reservedStock(0).build(),
            Product.builder().id(5L).name("키보드").price(BigDecimal.valueOf(100000)).stock(25).reservedStock(0).build(),
            Product.builder().id(6L).name("마우스").price(BigDecimal.valueOf(80000)).stock(30).reservedStock(0).build()
        );
        when(getPopularProductListUseCase.execute(3)).thenReturn(mockPopularProducts);
        
        // when
        List<ProductResponse> response = productController.getPopularProducts(request);

        // then
        assertThat(response).hasSize(5);
            assertThat(response.get(0).name()).isEqualTo("스마트폰");
            assertThat(response.get(1).name()).isEqualTo("노트북");
            assertThat(response.get(2).name()).isEqualTo("무선이어폰");
        }

        @Test
        @DisplayName("실패케이스: null 요청으로 인기 상품 조회")
        void getPopularProducts_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> productController.getPopularProducts(null))
                    .isInstanceOf(CommonException.InvalidRequest.class)
                    .hasMessage(CommonException.Messages.REQUEST_CANNOT_BE_NULL);
        }
    }

} 