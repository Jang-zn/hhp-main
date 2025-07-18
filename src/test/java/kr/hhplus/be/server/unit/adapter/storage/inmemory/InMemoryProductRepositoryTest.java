package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryProductRepository;
import kr.hhplus.be.server.domain.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryProductRepository 단위 테스트")
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
    }

    @Test
    @DisplayName("상품 저장 성공")
    void save_Success() {
        // given
        Product product = Product.builder()
                .name("노트북")
                .price(new BigDecimal("1200000"))
                .stock(50)
                .reservedStock(0)
                .build();

        // when
        Product savedProduct = productRepository.save(product);

        // then
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getId()).isNotNull();
        assertThat(savedProduct.getName()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("상품 ID로 조회 성공")
    void findById_Success() {
        // given
        Product product = Product.builder()
                .name("스마트폰")
                .price(new BigDecimal("800000"))
                .stock(100)
                .reservedStock(0)
                .build();
        Product savedProduct = productRepository.save(product);

        // when
        Optional<Product> foundProduct = productRepository.findById(savedProduct.getId());

        // then
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getName()).isEqualTo("스마트폰");
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회")
    void findById_NotFound() {
        // when
        Optional<Product> foundProduct = productRepository.findById(999L);

        // then
        assertThat(foundProduct).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideProductData")
    @DisplayName("다양한 상품 데이터로 저장")
    void save_WithDifferentProductData(String name, String price, int stock) {
        // given
        Product product = Product.builder()
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .reservedStock(0)
                .build();

        // when
        Product savedProduct = productRepository.save(product);

        // then
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getName()).isEqualTo(name);
        assertThat(savedProduct.getPrice()).isEqualTo(new BigDecimal(price));
        assertThat(savedProduct.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("페이지네이션으로 상품 조회")
    void findAll_WithPagination() {
        // given
        for (int i = 1; i <= 15; i++) {
            Product product = Product.builder()
                    .name("상품" + i)
                    .price(new BigDecimal("100000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            productRepository.save(product);
        }

        // when
        List<Product> products = productRepository.findAll(10, 0);

        // then
        assertThat(products).hasSize(10);
    }

    @Test
    @DisplayName("오프셋이 있는 페이지네이션")
    void findAll_WithOffset() {
        // given
        for (int i = 1; i <= 15; i++) {
            Product product = Product.builder()
                    .name("상품" + i)
                    .price(new BigDecimal("100000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            productRepository.save(product);
        }

        // when
        List<Product> products = productRepository.findAll(5, 10);

        // then
        assertThat(products).hasSize(5);
    }

    private static Stream<Arguments> provideProductData() {
        return Stream.of(
                Arguments.of("노트북", "1200000", 50),
                Arguments.of("스마트폰", "800000", 100),
                Arguments.of("태블릿", "600000", 30),
                Arguments.of("무선이어폰", "200000", 200)
        );
    }
} 