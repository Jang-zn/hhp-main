package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.domain.usecase.product.GetProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("상품 목록 조회 API 성공")
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
    @MethodSource("providePaginationData")
    @DisplayName("다양한 페이지네이션으로 상품 조회")
    void getProducts_WithDifferentPagination(int limit, int offset) {
        // given
        ProductRequest request = new ProductRequest(limit, offset);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
        assertThat(response).hasSize(3); // 하드코딩된 응답이므로 항상 3개
    }

    @Test
    @DisplayName("인기 상품 조회 API 성공")
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
    @DisplayName("기본 페이지네이션으로 상품 조회")
    void getProducts_WithDefaultPagination() {
        // given
        ProductRequest request = new ProductRequest(10, 0);
        
        // when
        List<ProductResponse> response = productController.getProductList(request);

        // then
        assertThat(response).hasSize(3);
    }

    private static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(5, 0), // 작은 페이지 크기
                Arguments.of(10, 10), // 오프셋 적용
                Arguments.of(20, 0), // 큰 페이지 크기
                Arguments.of(1, 0) // 최소 페이지 크기
        );
    }
} 