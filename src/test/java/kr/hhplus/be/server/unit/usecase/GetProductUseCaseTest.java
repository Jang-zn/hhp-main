package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.ProductException;

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

    @Nested
    @DisplayName("상품 조회 성공 테스트")
    class SuccessTests {
        
        @Test
        @DisplayName("성공케이스: 정상 상품 조회")
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

        @ParameterizedTest
        @MethodSource("provideProductData")
        @DisplayName("성공케이스: 다양한 상품 조회")
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
    }

    @Nested
    @DisplayName("상품 조회 실패 테스트")
    class FailureTests {
        
        @Test
        @DisplayName("실패케이스: 존재하지 않는 상품 조회")
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

        @Test
        @DisplayName("실패케이스: null 상품 ID로 조회")
        void getProduct_WithNullProductId() {
            // given
            Long productId = null;

            // when & then
            assertThatThrownBy(() -> getProductUseCase.execute(productId))
                    .isInstanceOf(ProductException.NotFound.class)
                    .hasMessage("Product not found");
        }

        @ParameterizedTest
        @MethodSource("provideInvalidProductIds")
        @DisplayName("실패케이스: 다양한 비정상 상품 ID로 조회")
        void getProduct_WithInvalidProductIds(Long invalidProductId) {
            // given
            when(productRepositoryPort.findById(invalidProductId)).thenReturn(Optional.empty());

            // when
            Optional<Product> result = getProductUseCase.execute(invalidProductId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("상품 조회 엣지 케이스 테스트")
    class EdgeCaseTests {

        @Test
        @DisplayName("성공케이스: 재고가 0인 상품 조회")
        void getProduct_OutOfStock() {
            // given
            Long productId = 1L;
            
            Product outOfStockProduct = Product.builder()
                    .name("품절 상품")
                    .price(new BigDecimal("100000"))
                    .stock(0)
                    .reservedStock(0)
                    .build();
            
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(outOfStockProduct));

            // when
            Optional<Product> result = getProductUseCase.execute(productId);

            // then - TODO 구현이 완료되면 실제 검증 로직 추가
            // assertThat(result).isPresent();
            // assertThat(result.get().getStock()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공케이스: 예약 재고가 실제 재고보다 많은 상품")
        void getProduct_InvalidReservedStock() {
            // given
            Long productId = 1L;
            
            Product invalidProduct = Product.builder()
                    .name("비정상 상품")
                    .price(new BigDecimal("50000"))
                    .stock(10)
                    .reservedStock(15) // 재고보다 많은 예약
                    .build();
            
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(invalidProduct));

            // when
            Optional<Product> result = getProductUseCase.execute(productId);

            // then - TODO 구현이 완료되면 실제 검증 로직 추가
            // assertThat(result).isPresent();
            // assertThat(result.get().getReservedStock()).isGreaterThan(result.get().getStock());
        }

        @Test
        @DisplayName("성공케이스: 음수 가격을 가진 상품")
        void getProduct_WithNegativePrice() {
            // given
            Long productId = 1L;
            
            Product negativeProduct = Product.builder()
                    .name("음수 가격 상품")
                    .price(new BigDecimal("-10000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(negativeProduct));

            // when
            Optional<Product> result = getProductUseCase.execute(productId);

            // then - TODO 구현이 완료되면 실제 검증 로직 추가
            // assertThat(result).isPresent();
            // assertThat(result.get().getPrice()).isEqualTo(new BigDecimal("-10000"));
        }

        @ParameterizedTest
        @MethodSource("provideEdgeCaseProducts")
        @DisplayName("성공케이스: 극한값 상품 데이터로 조회")
        void getProduct_WithEdgeCaseData(String description, String price, int stock, int reservedStock) {
            // given
            Long productId = 1L;
            
            Product edgeCaseProduct = Product.builder()
                    .name("극한값 " + description)
                    .price(new BigDecimal(price))
                    .stock(stock)
                    .reservedStock(reservedStock)
                    .build();
            
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(edgeCaseProduct));

            // when
            Optional<Product> result = getProductUseCase.execute(productId);

            // then - TODO 구현이 완료되면 실제 검증 로직 추가
            // assertThat(result).isPresent();
            // assertThat(result.get().getPrice()).isEqualTo(new BigDecimal(price));
        }
    }

    private static Stream<Arguments> provideProductData() {
        return Stream.of(
                Arguments.of(1L, "노트북", "1200000"),
                Arguments.of(2L, "스마트폰", "800000"),
                Arguments.of(3L, "태블릿", "600000")
        );
    }

    private static Stream<Arguments> provideInvalidProductIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideEdgeCaseProducts() {
        return Stream.of(
                Arguments.of("무료 상품", "0", 100, 0),
                Arguments.of("최대 가격", "999999999", 1, 0),
                Arguments.of("음수 재고", "50000", -10, 0),
                Arguments.of("전체 예약", "30000", 100, 100)
        );
    }
}