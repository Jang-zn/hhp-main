package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
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
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    

    private GetProductUseCase getProductUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getProductUseCase = new GetProductUseCase(productRepositoryPort, cachePort, keyGenerator);
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
    }

    @Nested
    @DisplayName("에러 처리 테스트")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("캐시 GET 실패 시에도 DB 조회는 정상 작동한다")
        void cacheGetFailure_DbStillWorks() {
            // given
            Long productId = 1L;
            Product expectedProduct = createProduct("테스트상품", "10000", 50);
            String cacheKey = "product:info:product_1";
            
            when(keyGenerator.generateProductCacheKey(productId)).thenReturn(cacheKey);
            when(cachePort.get(cacheKey, Product.class)).thenThrow(new RuntimeException("Cache GET failure"));
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(expectedProduct));
            
            // when
            Optional<Product> result = getProductUseCase.execute(productId);
            
            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("테스트상품");
            
            // 캐시 실패에도 불구하고 DB는 호출되어야 함
            verify(productRepositoryPort).findById(productId);
        }
        
        @Test
        @DisplayName("캐시 PUT 실패 시에도 DB 결과는 정상 반환된다")
        void cachePutFailure_DbResultStillReturned() {
            // given
            Long productId = 2L;
            Product expectedProduct = createProduct("테스트상품2", "20000", 30);
            String cacheKey = "product:info:product_2";
            
            when(keyGenerator.generateProductCacheKey(productId)).thenReturn(cacheKey);
            when(cachePort.get(cacheKey, Product.class)).thenReturn(null); // 캐시 미스
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(expectedProduct));
            doThrow(new RuntimeException("Cache PUT failure")).when(cachePort)
                    .put(eq(cacheKey), eq(expectedProduct), anyInt());
            
            // when
            Optional<Product> result = getProductUseCase.execute(productId);
            
            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("테스트상품2");
            
            verify(productRepositoryPort).findById(productId);
        }
        
        @Test
        @DisplayName("상품 목록 조회 시 캐시 실패해도 DB 조회는 정상 작동한다")
        void productListCacheFailure_DbStillWorks() {
            // given
            int limit = 10;
            int offset = 0;
            List<Product> expectedProducts = List.of(
                    createProduct("상품1", "10000", 10),
                    createProduct("상품2", "20000", 20)
            );
            String cacheKey = "product:list:limit_10_offset_0";
            
            when(keyGenerator.generateProductListCacheKey(limit, offset)).thenReturn(cacheKey);
            when(cachePort.getList(cacheKey)).thenThrow(new RuntimeException("Cache LIST failure"));
            when(productRepositoryPort.findAllWithPagination(limit, offset)).thenReturn(expectedProducts);
            
            // when
            List<Product> result = getProductUseCase.execute(limit, offset);
            
            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("상품1");
            
            verify(productRepositoryPort).findAllWithPagination(limit, offset);
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