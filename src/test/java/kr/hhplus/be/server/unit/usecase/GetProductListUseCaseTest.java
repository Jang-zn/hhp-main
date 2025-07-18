package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.product.GetProductListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("GetProductListUseCase 단위 테스트")
class GetProductListUseCaseTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetProductListUseCase getProductListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getProductListUseCase = new GetProductListUseCase(productRepositoryPort, cachePort);
    }

    @Test
    @DisplayName("상품 목록 조회 성공")
    void getProducts_Success() {
        // given
        int limit = 10;
        int offset = 0;
        
        List<Product> products = List.of(
                createProduct(1L, "노트북", "1200000", 50),
                createProduct(2L, "스마트폰", "800000", 100),
                createProduct(3L, "태블릿", "600000", 30)
        );
        
        when(productRepositoryPort.findAll(limit, offset)).thenReturn(products);

        // when
        List<Product> result = getProductListUseCase.execute(limit, offset);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("노트북");
    }

    @ParameterizedTest
    @MethodSource("providePaginationData")
    @DisplayName("다양한 페이지네이션으로 상품 조회")
    void getProducts_WithDifferentPagination(int limit, int offset) {
        // given
        List<Product> products = List.of(
                createProduct(1L, "상품1", "100000", 10),
                createProduct(2L, "상품2", "200000", 20)
        );
        
        when(productRepositoryPort.findAll(limit, offset)).thenReturn(products);

        // when
        List<Product> result = getProductListUseCase.execute(limit, offset);

        // then
        assertThat(result).hasSize(2);
    }

    private static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(5, 0), // 첫 페이지
                Arguments.of(10, 10), // 두 번째 페이지
                Arguments.of(20, 0) // 큰 페이지 크기
        );
    }

    private Product createProduct(Long id, String name, String price, int stock) {
        return Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .reservedStock(0)
                .build();
    }
} 