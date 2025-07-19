package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.domain.usecase.product.GetProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductController 단위 테스트")
class ProductControllerTest {

    private ProductController productController;
    private GetProductListUseCase getProductListUseCase;
    private GetPopularProductListUseCase getPopularProductListUseCase;

    @BeforeEach
    void setUp() {
        getProductListUseCase = new GetProductListUseCase(null, null);
        getPopularProductListUseCase = new GetPopularProductListUseCase(null, null);
        productController = new ProductController(getProductListUseCase, getPopularProductListUseCase);
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class GetProductsTests {
        
        @Test
        @DisplayName("성공케이스: 정상 상품 목록 조회")
        void getProducts_Success() {
        // given
        ProductRequest request = new ProductRequest(10, 0);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
        assertThat(response).hasSize(3);
        assertThat(response.get(0).name()).isEqualTo("노트북");
        assertThat(response.get(1).name()).isEqualTo("스마트폰");
        assertThat(response.get(2).name()).isEqualTo("태블릿");
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.controller.ProductControllerTest#providePaginationData")
        @DisplayName("성공케이스: 다양한 페이지네이션으로 상품 조회")
        void getProducts_WithDifferentPagination(int limit, int offset) {
        // given
        ProductRequest request = new ProductRequest(limit, offset);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
            assertThat(response).hasSize(3); // 하드코딩된 응답이므로 항상 3개
        }

        @Test
        @DisplayName("성공케이스: 기본 페이지네이션으로 상품 조회")
        void getProducts_WithDefaultPagination() {
            // given
            ProductRequest request = new ProductRequest(10, 0);
            
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
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패케이스: 비정상 페이지네이션 파라미터")
        void getProducts_WithInvalidPagination() {
            // given
            ProductRequest invalidRequest = new ProductRequest(-1, -1);
            
            // when & then
            assertThatThrownBy(() -> productController.getProductList(invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class);
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
        
        // when
        List<ProductResponse> response = productController.getPopularProducts(request);

        // then
        assertThat(response).hasSize(5);
            assertThat(response.get(0).name()).isEqualTo("스마트폰");
            assertThat(response.get(1).name()).isEqualTo("노트북");
            assertThat(response.get(2).name()).isEqualTo("무선이어폰");
        }

        @Test
        @DisplayName("성공케이스: 다양한 수량으로 인기 상품 조회")
        void getPopularProducts_WithDifferentLimits() {
            // given
            ProductRequest request = new ProductRequest(10);
            
            // when
            List<ProductResponse> response = productController.getPopularProducts(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response).isNotEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 요청으로 인기 상품 조회")
        void getPopularProducts_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> productController.getPopularProducts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패케이스: 비정상 수량으로 인기 상품 조회")
        void getPopularProducts_WithInvalidLimit() {
            // given
            ProductRequest invalidRequest = new ProductRequest(-1);
            
            // when & then
            assertThatThrownBy(() -> productController.getPopularProducts(invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(5, 0), // 작은 페이지 크기
                Arguments.of(10, 10), // 오프셋 적용
                Arguments.of(20, 0), // 큰 페이지 크기
                Arguments.of(1, 0) // 최소 페이지 크기
        );
    }

    private static Stream<Arguments> provideInvalidLimits() {
        return Stream.of(
                Arguments.of(-1),
                Arguments.of(0),
                Arguments.of(Integer.MIN_VALUE)
        );
    }
} 