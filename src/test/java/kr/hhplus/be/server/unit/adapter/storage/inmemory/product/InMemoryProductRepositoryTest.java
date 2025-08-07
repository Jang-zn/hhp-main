package kr.hhplus.be.server.unit.adapter.storage.inmemory.product;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryProductRepository;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static kr.hhplus.be.server.util.TestAssertions.ProductAssertions;
import static kr.hhplus.be.server.util.TestAssertions.CommonAssertions;

/**
 * InMemoryProductRepository 비즈니스 시나리오 테스트
 * 
 * Why: 상품 저장소의 핵심 기능이 이커머스 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 상품 관리 시나리오를 반영한 테스트로 구성
 */
@DisplayName("상품 저장소 비즈니스 시나리오")
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
    }

    @Test
    @DisplayName("유효한 상품을 등록할 수 있다")
    void canRegisterValidProduct() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct().build();

        // When
        Product saved = productRepository.save(product);

        // Then
        CommonAssertions.assertEntityValid(saved);
        assertThat(saved.getName()).isEqualTo(product.getName());
        assertThat(saved.getPrice()).isEqualByComparingTo(product.getPrice());
        assertThat(saved.getStock()).isEqualTo(product.getStock());
    }

    @ParameterizedTest
    @MethodSource("provideDiverseProductData")
    @DisplayName("다양한 가격과 재고의 상품을 등록할 수 있다")
    void canRegisterDiverseProducts(String name, String price, int stock) {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .name(name)
            .price(new BigDecimal(price))
            .stock(stock)
            .build();

        // When
        Product saved = productRepository.save(product);

        // Then
        CommonAssertions.assertEntityValid(saved);
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal(price));
        assertThat(saved.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("재고가 없는 상품도 등록할 수 있다")
    void canRegisterOutOfStockProduct() {
        // Given
        Product product = TestBuilder.ProductBuilder.outOfStockProduct().build();

        // When
        Product saved = productRepository.save(product);

        // Then
        CommonAssertions.assertEntityValid(saved);
        assertThat(saved.getStock()).isEqualTo(0);
        assertThat(saved.hasAvailableStock(1)).isFalse();
    }

    @Test
    @DisplayName("일부 재고가 예약된 상품을 등록할 수 있다")
    void canRegisterPartiallyReservedProduct() {
        // Given
        Product product = TestBuilder.ProductBuilder.partiallyReservedProduct().build();

        // When
        Product saved = productRepository.save(product);

        // Then
        CommonAssertions.assertEntityValid(saved);
        ProductAssertions.assertReservationSuccess(saved);
    }

    @Test
    @DisplayName("null 상품 저장 시 예외가 발생한다")
    void throwsExceptionWhenSavingNullProduct() {
        // When & Then
        assertThatThrownBy(() -> productRepository.save(null))
            .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("등록된 상품을 ID로 조회할 수 있다")
    void canFindProductById() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct().build();
        Product saved = productRepository.save(product);

        // When
        Optional<Product> found = productRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        CommonAssertions.assertEntityValid(found.get());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindingNonExistentProduct() {
        // When
        Optional<Product> found = productRepository.findById(999L);

        // Then
        assertThat(found).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideInvalidProductIds")
    @DisplayName("유효하지 않은 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindingWithInvalidIds(Long invalidId) {
        // When
        Optional<Product> found = productRepository.findById(invalidId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("저장된 상품들을 개별적으로 조회할 수 있다")
    void canFindMultipleProductsSeparately() {
        // Given
        Product product1 = TestBuilder.ProductBuilder.defaultProduct().id(1L).name("상품1").build();
        Product product2 = TestBuilder.ProductBuilder.defaultProduct().id(2L).name("상품2").build();
        
        productRepository.save(product1);
        productRepository.save(product2);

        // When & Then
        Optional<Product> found1 = productRepository.findById(1L);
        Optional<Product> found2 = productRepository.findById(2L);
        
        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getName()).isEqualTo("상품1");
        assertThat(found2.get().getName()).isEqualTo("상품2");
    }

    @Test
    @DisplayName("상품의 재고를 예약할 수 있다")
    void canReserveProductStock() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .withStock(100, 20)
            .build();
        Product saved = productRepository.save(product);
        int reserveQuantity = 30;

        // When
        saved.reserveStock(reserveQuantity);
        Product updated = productRepository.save(saved);

        // Then
        ProductAssertions.assertStockReserved(updated, 100, 20, reserveQuantity);
    }

    @Test
    @DisplayName("재고 부족 시 예약이 차단된다")
    void blocksReservationWhenInsufficientStock() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .withStock(10, 8) // 가용 재고 2개
            .build();
        Product saved = productRepository.save(product);

        // When & Then
        assertThatThrownBy(() -> saved.reserveStock(5))
            .isInstanceOf(ProductException.OutOfStock.class);
    }

    @Test
    @DisplayName("재고 상태를 업데이트할 수 있다")
    void canUpdateStockStatus() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .withStock(100, 30)
            .build();
        Product saved = productRepository.save(product);

        // When - 재고를 변경한 상품으로 업데이트
        Product updated = TestBuilder.ProductBuilder.defaultProduct()
            .id(saved.getId())
            .name(saved.getName())
            .price(saved.getPrice())
            .withStock(80, 10) // 재고 확정으로 인한 변화
            .build();
        Product result = productRepository.save(updated);

        // Then
        assertThat(result.getStock()).isEqualTo(80);
        assertThat(result.getReservedStock()).isEqualTo(10);
        ProductAssertions.assertReservationSuccess(result);
    }

    @Test
    @DisplayName("서로 다른 상품들을 동시에 등록할 수 있다")
    void canRegisterDifferentProductsConcurrently() {
        // Given
        int numberOfProducts = 10;

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfProducts, () -> {
                Product product = TestBuilder.ProductBuilder.defaultProduct()
                    .id(System.nanoTime())
                    .name("상품_" + System.nanoTime())
                    .build();
                return productRepository.save(product);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfProducts);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일한 상품의 재고를 동시에 예약할 때 적절히 처리된다")
    void handlesConcurrentStockReservationsProperly() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .id(1L)
            .withStock(100, 0)
            .build();
        productRepository.save(product);
        
        int numberOfReservations = 10;
        int reserveQuantityEach = 5; // 총 50개 예약 시도

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfReservations, () -> {
                Product currentProduct = productRepository.findById(1L).get();
                currentProduct.reserveStock(reserveQuantityEach);
                return productRepository.save(currentProduct);
            });

        // Then
        assertThat(result.getSuccessCount()).isGreaterThan(0);
        
        Product finalProduct = productRepository.findById(1L).get();
        ProductAssertions.assertReservationSuccess(finalProduct);
    }

    @Test
    @DisplayName("상품 조회와 업데이트가 동시에 실행될 수 있다")
    void canReadAndUpdateConcurrently() {
        // Given
        Product product = TestBuilder.ProductBuilder.defaultProduct()
            .id(1L)
            .name("동시성테스트상품")
            .build();
        productRepository.save(product);

        // When - 읽기와 쓰기 작업을 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeMultipleTasks(List.of(
                // 읽기 작업
                () -> {
                    for (int i = 0; i < 20; i++) {
                        productRepository.findById(1L);
                        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                },
                // 업데이트 작업
                () -> {
                    for (int i = 0; i < 10; i++) {
                        Product current = productRepository.findById(1L).get();
                        Product updated = TestBuilder.ProductBuilder.defaultProduct()
                            .id(1L)
                            .name("업데이트상품_" + i)
                            .build();
                        productRepository.save(updated);
                        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            ));

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        Product finalProduct = productRepository.findById(1L).get();
        CommonAssertions.assertEntityValid(finalProduct);
    }

    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideDiverseProductData() {
        return Stream.of(
            Arguments.of("노트북", "1200000", 50),
            Arguments.of("마우스", "25000", 200),
            Arguments.of("키보드", "89000", 150)
        );
    }

    private static Stream<Arguments> provideInvalidProductIds() {
        return Stream.of(
            Arguments.of(0L),
            Arguments.of(-1L),
            Arguments.of(-999L)
        );
    }
}