package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetProductUseCase 단위 테스트")
class GetProductUseCaseTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;
    

    private GetProductUseCase getProductUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getProductUseCase = new GetProductUseCase(productRepositoryPort);
    }

    @Nested
    @DisplayName("단일 상품 조회 테스트")
    class SingleProductTests {
        
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

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("노트북");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 상품 조회")
        void getProduct_NotFound() {
            // given
            Long productId = 999L;
            
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.empty());

            // when
            Optional<Product> result = getProductUseCase.execute(productId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 상품 ID로 조회")
        void getProduct_WithNullProductId() {
            // when & then
            assertThatThrownBy(() -> getProductUseCase.execute((Long) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("상품 ID는 null일 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class ProductListTests {
        
        @Test
        @DisplayName("성공케이스: 상품 목록 조회 성공")
        void getProductList_Success() {
            // given
            int limit = 10;
            int offset = 0;
            
            List<Product> products = List.of(
                    createProduct("노트북", "1200000", 50),
                    createProduct("스마트폰", "800000", 100),
                    createProduct("태블릿", "600000", 30)
            );
            
            when(productRepositoryPort.findAllWithPagination(limit, offset)).thenReturn(products);

            // when
            List<Product> result = getProductUseCase.execute(limit, offset);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getName()).isEqualTo("노트북");
        }
        
        @Test
        @DisplayName("성공케이스: 빈 상품 목록 조회")
        void getProductList_EmptyList() {
            // given
            int limit = 10;
            int offset = 0;
            
            when(productRepositoryPort.findAllWithPagination(limit, offset)).thenReturn(Collections.emptyList());

            // when
            List<Product> result = getProductUseCase.execute(limit, offset);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("실패케이스: 음수 limit")
        void getProductList_WithNegativeLimit() {
            // when & then
            assertThatThrownBy(() -> getProductUseCase.execute(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit must be greater than 0");
        }
        
        @Test
        @DisplayName("실패케이스: 음수 offset")
        void getProductList_WithNegativeOffset() {
            // when & then
            assertThatThrownBy(() -> getProductUseCase.execute(10, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Offset must be non-negative");
        }
        
        @Test
        @DisplayName("실패케이스: 과도한 limit")
        void getProductList_WithExcessiveLimit() {
            // when & then
            assertThatThrownBy(() -> getProductUseCase.execute(10000, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit exceeds maximum allowed (1000)");
        }
    }
    
    private Product createProduct(String name, String price, int stock) {
        return Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .reservedStock(0)
                .build();
    }
}