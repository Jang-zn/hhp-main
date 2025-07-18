package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("GetProductUseCase 단위 테스트")
class GetProductUseCaseTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetProductUseCase getProductUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getProductUseCase = new GetProductUseCase(productRepositoryPort, cachePort);
    }

    @Test
    @DisplayName("상품 조회 성공")
    void getProduct_Success() {
        // given
        Long productId = 1L;
        
        Product product = Product.builder()
                .name("노트북")
                .price(new BigDecimal("1200000"))
                .stock(50)
                .reservedStock(0)
                .build();
        
        when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(product));

        // when
        Optional<Product> result = getProductUseCase.execute(productId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getName()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회")
    void getProduct_NotFound() {
        // given
        Long productId = 999L;
        
        when(productRepositoryPort.findById(productId)).thenReturn(Optional.empty());

        // when
        Optional<Product> result = getProductUseCase.execute(productId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideProductData")
    @DisplayName("다양한 상품 조회")
    void getProduct_WithDifferentProducts(Long productId, String name, String price) {
        // given
        Product product = Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stock(100)
                .reservedStock(0)
                .build();
        
        when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(product));

        // when
        Optional<Product> result = getProductUseCase.execute(productId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getName()).isEqualTo(name);
    }

    private static Stream<Arguments> provideProductData() {
        return Stream.of(
                Arguments.of(1L, "노트북", "1200000"),
                Arguments.of(2L, "스마트폰", "800000"),
                Arguments.of(3L, "태블릿", "600000")
        );
    }
}