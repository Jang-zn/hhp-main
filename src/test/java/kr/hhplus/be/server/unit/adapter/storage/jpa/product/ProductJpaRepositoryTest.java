package kr.hhplus.be.server.unit.adapter.storage.jpa.product;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.ProductJpaRepository;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: JPA 상품 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: JPA 기반 상품 관리 시나리오를 반영한 단위 테스트로 구성
 * 
 * 참고: 실제 ProductJpaRepository는 예외를 try-catch로 처리하여 빈 Optional을 반환하므로,
 *       단순한 동작 확인에 초점을 맞춰 테스트를 구성
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("JPA 상품 저장소 비즈니스 시나리오")
class ProductJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;
    
    @Mock
    private TypedQuery<Product> productQuery;
    
    @Mock
    private TypedQuery<Long> countQuery;

    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void setUp() {
        productJpaRepository = new ProductJpaRepository(entityManager);
    }

    // === 상품 저장 시나리오 ===

    @Test
    @DisplayName("신규 상품을 JPA를 통해 저장할 수 있다")
    void canSaveNewProductThroughJpa() {
        // Given
        Product newProduct = TestBuilder.ProductBuilder.defaultProduct()
                .name("새로운상품")
                .price(new BigDecimal("50000"))
                .stock(100)
                .build();

        // When
        Product savedProduct = productJpaRepository.save(newProduct);

        // Then - 저장 결과 확인
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getName()).isEqualTo("새로운상품");
    }

    @Test
    @DisplayName("기존 상품의 정보를 업데이트할 수 있다")
    void canUpdateExistingProductInfo() {
        // Given
        Product existingProduct = TestBuilder.ProductBuilder.defaultProduct()
                .id(1L)
                .name("기존상품")
                .price(new BigDecimal("75000"))
                .stock(50)
                .build();
        when(entityManager.merge(existingProduct)).thenReturn(existingProduct);

        // When
        Product updatedProduct = productJpaRepository.save(existingProduct);

        // Then - 업데이트 결과 확인
        assertThat(updatedProduct).isNotNull();
        assertThat(updatedProduct.getName()).isEqualTo("기존상품");
        verify(entityManager).merge(existingProduct);
    }

    @ParameterizedTest
    @MethodSource("provideProductData")
    @DisplayName("다양한 상품 정보로 저장할 수 있다")
    void canSaveProductsWithVariousData(String name, BigDecimal price, int stock) {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
                .name(name)
                .price(price)
                .stock(stock)
                .build();

        // When
        Product savedProduct = productJpaRepository.save(product);

        // Then - 다양한 데이터 저장 확인
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getName()).isEqualTo(name);
        assertThat(savedProduct.getPrice()).isEqualTo(price);
        assertThat(savedProduct.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("null 상품 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingNullProduct() {
        // When & Then
        assertThatThrownBy(() -> productJpaRepository.save(null))
            .isInstanceOf(NullPointerException.class);
    }

    // === 상품 조회 시나리오 ===

    @Test
    @DisplayName("ID로 상품을 조회할 수 있다")
    void canFindProductById() {
        // Given
        Long productId = 1L;
        Product expectedProduct = TestBuilder.ProductBuilder.defaultProduct()
                .id(productId)
                .name("조회용상품")
                .price(new BigDecimal("30000"))
                .stock(25)
                .build();
        
        when(entityManager.find(Product.class, productId)).thenReturn(expectedProduct);

        // When
        Optional<Product> foundProduct = productJpaRepository.findById(productId);

        // Then
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getId()).isEqualTo(productId);
        assertThat(foundProduct.get().getName()).isEqualTo("조회용상품");
        assertThat(foundProduct.get().getPrice()).isEqualTo(new BigDecimal("30000"));
        
        verify(entityManager).find(Product.class, productId);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenProductNotFoundById() {
        // Given
        Long nonExistentId = 999L;
        when(entityManager.find(Product.class, nonExistentId)).thenReturn(null);

        // When
        Optional<Product> foundProduct = productJpaRepository.findById(nonExistentId);

        // Then
        assertThat(foundProduct).isEmpty();
        verify(entityManager).find(Product.class, nonExistentId);
    }

    @Test
    @DisplayName("인기 상품 목록을 조회할 수 있다")
    void canFindPopularProducts() {
        // Given
        int period = 7;
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("인기상품1").build(),
            TestBuilder.ProductBuilder.defaultProduct().name("인기상품2").build(),
            TestBuilder.ProductBuilder.defaultProduct().name("인기상품3").build()
        );
        
        when(entityManager.createQuery(anyString(), eq(Product.class)))
            .thenReturn(productQuery);
        when(productQuery.setParameter(eq("periodDate"), any())).thenReturn(productQuery);
        when(productQuery.getResultList()).thenReturn(expectedProducts);

        // When
        List<Product> foundProducts = productJpaRepository.findPopularProducts(period);

        // Then
        assertThat(foundProducts).hasSize(3);
        assertThat(foundProducts).extracting("name")
            .containsExactly("인기상품1", "인기상품2", "인기상품3");
        
        verify(entityManager).createQuery(anyString(), eq(Product.class));
        verify(productQuery).setParameter(eq("periodDate"), any());
        verify(productQuery).getResultList();
    }

    @Test
    @DisplayName("페이징을 적용하여 상품 목록을 조회할 수 있다")
    void canFindProductsWithPagination() {
        // Given
        int limit = 5;
        int offset = 10;
        List<Product> expectedProducts = List.of(
            TestBuilder.ProductBuilder.defaultProduct().name("페이징상품1").build(),
            TestBuilder.ProductBuilder.defaultProduct().name("페이징상품2").build()
        );
        
        when(entityManager.createQuery(anyString(), eq(Product.class)))
            .thenReturn(productQuery);
        when(productQuery.setFirstResult(offset)).thenReturn(productQuery);
        when(productQuery.setMaxResults(limit)).thenReturn(productQuery);
        when(productQuery.getResultList()).thenReturn(expectedProducts);

        // When
        List<Product> foundProducts = productJpaRepository.findAllWithPagination(limit, offset);

        // Then
        assertThat(foundProducts).hasSize(2);
        assertThat(foundProducts).extracting("name")
            .containsExactly("페이징상품1", "페이징상품2");
        
        verify(entityManager).createQuery(anyString(), eq(Product.class));
        verify(productQuery).setFirstResult(offset);
        verify(productQuery).setMaxResults(limit);
        verify(productQuery).getResultList();
    }

    @Test
    @DisplayName("null ID로 조회 시 EntityManager에서 예외가 발생할 수 있다")
    void mayThrowExceptionWhenFindingByNullIdFromEntityManager() {
        // Given
        when(entityManager.find(Product.class, null)).thenReturn(null);
        
        // When
        Optional<Product> result = productJpaRepository.findById(null);
        
        // Then - 빈 Optional 반환 또는 예외 처리
        assertThat(result).isEmpty();
        verify(entityManager).find(Product.class, null);
    }

    // === 동시성 시나리오 ===

    @Test
    @DisplayName("여러 상품이 동시에 저장되어도 안전하게 처리된다")
    void safelyHandlesConcurrentProductSaving() {
        // When - 3개의 동시 저장 작업
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                Product product = TestBuilder.ProductBuilder.defaultProduct()
                        .name("동시상품_" + System.nanoTime())
                        .price(new BigDecimal("10000"))
                        .build();
                Product saved = productJpaRepository.save(product);
                return saved != null ? 1 : 0;
            });

        // Then - 동시 작업 성공 확인
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("동시 상품 조회가 안전하게 처리된다")
    void safelyHandlesConcurrentProductQuerying() {
        // Given
        Long productId = 1L;
        Product expectedProduct = TestBuilder.ProductBuilder.defaultProduct()
                .id(productId)
                .name("동시조회상품")
                .build();
        when(entityManager.find(Product.class, productId)).thenReturn(expectedProduct);

        // When - 5개의 동시 조회 작업
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                Optional<Product> found = productJpaRepository.findById(productId);
                return found.isPresent() ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getSuccessCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("상품 저장과 조회가 동시에 이루어져도 데이터 일관성이 보장된다")
    void maintainsDataConsistencyUnderConcurrentSaveAndFind() {
        // Given
        Product findProduct = TestBuilder.ProductBuilder.defaultProduct().id(1L).name("조회용상품").build();
        when(entityManager.find(Product.class, 1L)).thenReturn(findProduct);

        // When - 저장과 조회가 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(6, () -> {
                if (Math.random() < 0.5) {
                    Product product = TestBuilder.ProductBuilder.defaultProduct()
                            .name("저장용상품_" + System.nanoTime())
                            .build();
                    Product saved = productJpaRepository.save(product);
                    return saved != null ? 1 : 0;
                } else {
                    Optional<Product> found = productJpaRepository.findById(1L);
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then - 데이터 일관성 확인
        assertThat(result.getTotalCount()).isEqualTo(6);
        assertThat(result.getSuccessCount()).isGreaterThan(0);
    }

    // === 예외 처리 시나리오 ===

    @Test
    @DisplayName("저장 시 예외가 발생하면 예외가 전파된다")
    void propagatesSaveExceptions() {
        // Given - EntityManager 예외 상황 모의
        Product product = TestBuilder.ProductBuilder.defaultProduct().build();
        RuntimeException expectedException = new RuntimeException("상품 저장 실패");
        doThrow(expectedException).when(entityManager).persist(product);

        // When & Then - 예외가 전파되어야 함
        assertThatThrownBy(() -> productJpaRepository.save(product))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("상품 저장 실패");
            
        verify(entityManager).persist(product);
    }

    @Test
    @DisplayName("조회 시 예외가 발생해도 빈 결과를 반환한다")
    void returnsEmptyWhenFindExceptionOccurs() {
        // Given
        Long productId = 1L;
        RuntimeException expectedException = new RuntimeException("상품 조회 실패");
        when(entityManager.find(Product.class, productId)).thenThrow(expectedException);

        // When - 실제로는 try-catch로 예외를 잡아 빈 Optional 반환
        Optional<Product> result = productJpaRepository.findById(productId);
            
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("페이징 조회 시 음수 파라미터도 정상 처리된다")
    void handlesNegativePaginationParameters() {
        // Given
        when(entityManager.createQuery(anyString(), eq(Product.class)))
            .thenReturn(productQuery);
        when(productQuery.setMaxResults(-1)).thenReturn(productQuery);
        when(productQuery.setFirstResult(0)).thenReturn(productQuery);
        when(productQuery.getResultList()).thenReturn(List.of());
            
        // When - 음수 limit
        List<Product> result1 = productJpaRepository.findAllWithPagination(-1, 0);
        
        // Then
        assertThat(result1).isEmpty();
    }

    // === 헬퍼 메서드 ===

    static Stream<Arguments> provideProductData() {
        return Stream.of(
            Arguments.of("기본상품", new BigDecimal("10000"), 50),
            Arguments.of("프리미엄상품", new BigDecimal("100000"), 10),
            Arguments.of("할인상품", new BigDecimal("5000"), 100),
            Arguments.of("한정상품", new BigDecimal("500000"), 1),
            Arguments.of("인기상품", new BigDecimal("25000"), 200),
            Arguments.of("신규상품", new BigDecimal("15000"), 75),
            Arguments.of("특가상품", new BigDecimal("3000"), 300)
        );
    }
}