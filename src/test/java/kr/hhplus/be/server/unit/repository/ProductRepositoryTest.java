package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JPA 상품 저장소 비즈니스 시나리오")
class ProductRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private ProductRepositoryPort productRepositoryPort;

    @Test
    @DisplayName("새로운 상품을 저장할 수 있다")
    void canSaveNewProduct() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
                .name("테스트 상품")
                .price(new BigDecimal("10000"))
                .stock(100)
                .reservedStock(0) // 필수 필드 추가
                .build();

        // When
        Product savedProduct = productRepositoryPort.save(product);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Product foundProduct = testEntityManager.find(Product.class, savedProduct.getId());
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getName()).isEqualTo("테스트 상품");
        assertThat(foundProduct.getPrice()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(foundProduct.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("ID로 상품을 조회할 수 있다")
    void canFindProductById() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
                .name("조회 테스트 상품")
                .price(new BigDecimal("5000"))
                .stock(50)
                .reservedStock(0)
                .build();
        Product savedProduct = testEntityManager.persistAndFlush(product);
        testEntityManager.clear();

        // When
        Optional<Product> foundProduct = productRepositoryPort.findById(savedProduct.getId());

        // Then
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getName()).isEqualTo("조회 테스트 상품");
    }

    @Test
    @DisplayName("기본 조회 기능만 테스트")
    void canPerformBasicQueries() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
                .name("테스트상품")
                .price(new BigDecimal("3000"))
                .stock(10)
                .reservedStock(0)
                .build();
        testEntityManager.persistAndFlush(product);
        testEntityManager.clear();

        // When & Then - 기본 조회 기능 확인
        Optional<Product> found = productRepositoryPort.findById(product.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트상품");
    }

    @ParameterizedTest
    @MethodSource("provideProductData")
    @DisplayName("다양한 상품 정보로 저장할 수 있다")
    void canSaveProductWithVariousData(String name, BigDecimal price, int stock) {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
                .name(name)
                .price(price)
                .stock(stock)
                .reservedStock(0) // 필수 필드 추가
                .build();

        // When
        Product savedProduct = productRepositoryPort.save(product);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Product foundProduct = testEntityManager.find(Product.class, savedProduct.getId());
        assertThat(foundProduct.getName()).isEqualTo(name);
        assertThat(foundProduct.getPrice()).isEqualByComparingTo(price);
        assertThat(foundProduct.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenProductNotFoundById() {
        // Given
        Long nonExistentId = 999L;

        // When
        Optional<Product> foundProduct = productRepositoryPort.findById(nonExistentId);

        // Then
        assertThat(foundProduct).isEmpty();
    }

    @Test
    @DisplayName("null 상품 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullProduct() {
        // When & Then
        assertThatThrownBy(() -> productRepositoryPort.save(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("null ID 목록에 대해 빈 리스트 반환")
    void findByIds_WithNullIds_ReturnsEmptyList() {
        // when
        List<Product> result = productRepositoryPort.findByIds(null);
        
        // then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("빈 ID 목록에 대해 빈 리스트 반환")
    void findByIds_WithEmptyIds_ReturnsEmptyList() {
        // when
        List<Product> result = productRepositoryPort.findByIds(Collections.emptyList());
        
        // then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("유효한 ID 목록에 대해 ID 순으로 정렬된 결과 반환")
    void findByIds_WithValidIds_ReturnsIdSortedResults() {
        // given
        Product product1 = testEntityManager.persistAndFlush(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("Product 1")
                .price(new BigDecimal("1000"))
                .stock(10)
                .reservedStock(0)
                .build()
        );
        Product product2 = testEntityManager.persistAndFlush(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("Product 2")
                .price(new BigDecimal("2000"))
                .stock(20)
                .reservedStock(0)
                .build()
        );
        Product product3 = testEntityManager.persistAndFlush(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("Product 3")
                .price(new BigDecimal("3000"))
                .stock(30)
                .reservedStock(0)
                .build()
        );
        testEntityManager.clear();
        
        List<Long> ids = Arrays.asList(product3.getId(), product1.getId(), product2.getId());
        
        // when
        List<Product> result = productRepositoryPort.findByIds(ids);
        
        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(product1.getId());
        assertThat(result.get(1).getId()).isEqualTo(product2.getId());
        assertThat(result.get(2).getId()).isEqualTo(product3.getId());
    }
    
    static Stream<Arguments> provideProductData() {
        return Stream.of(
                Arguments.of("기본 상품", new BigDecimal("1000"), 50),
                Arguments.of("고가 상품", new BigDecimal("100000"), 5),
                Arguments.of("저가 상품", new BigDecimal("500"), 200),
                Arguments.of("재고 부족 상품", new BigDecimal("5000"), 1),
                Arguments.of("재고 많은 상품", new BigDecimal("2000"), 1000)
        );
    }
}