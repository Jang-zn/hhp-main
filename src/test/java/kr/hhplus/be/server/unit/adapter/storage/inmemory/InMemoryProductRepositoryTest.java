package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryProductRepository;
import kr.hhplus.be.server.domain.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryProductRepository 단위 테스트")
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
    }

    @Nested
    @DisplayName("상품 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 상품 저장")
        void save_Success() {
        // given
        Product product = Product.builder()
                .id(1L)
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

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryProductRepositoryTest#provideProductData")
        @DisplayName("성공케이스: 다양한 상품 데이터로 저장")
        void save_WithDifferentProductData(String name, String price, int stock) {
            // given
            Product product = Product.builder()
                    .id(2L)
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
        @DisplayName("성공케이스: 재고가 0인 상품 저장")
        void save_WithZeroStock() {
            // given
            Product product = Product.builder()
                    .id(3L)
                    .name("품절 상품")
                    .price(new BigDecimal("100000"))
                    .stock(0)
                    .reservedStock(0)
                    .build();

            // when
            Product savedProduct = productRepository.save(product);

            // then
            assertThat(savedProduct).isNotNull();
            assertThat(savedProduct.getStock()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공케이스: 재고보다 예약 재고가 많은 상품")
        void save_WithReservedStockGreaterThanStock() {
            // given
            Product product = Product.builder()
                    .id(4L)
                    .name("비정상 상품")
                    .price(new BigDecimal("50000"))
                    .stock(10)
                    .reservedStock(15)
                    .build();

            // when
            Product savedProduct = productRepository.save(product);

            // then
            assertThat(savedProduct).isNotNull();
            assertThat(savedProduct.getReservedStock()).isGreaterThan(savedProduct.getStock());
        }

        @Test
        @DisplayName("성공케이스: 음수 재고를 가진 상품")
        void save_WithNegativeStock() {
            // given
            Product product = Product.builder()
                    .id(5L)
                    .name("음수 재고 상품")
                    .price(new BigDecimal("30000"))
                    .stock(-5)
                    .reservedStock(0)
                    .build();

            // when
            Product savedProduct = productRepository.save(product);

            // then
            assertThat(savedProduct).isNotNull();
            assertThat(savedProduct.getStock()).isEqualTo(-5);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryProductRepositoryTest#provideEdgeCasePrices")
        @DisplayName("성공케이스: 극한값 가격으로 상품 저장")
        void save_WithEdgeCasePrices(String description, String price) {
            // given
            Product product = Product.builder()
                    .id(6L)
                    .name("극한값 가격 상품")
                    .price(new BigDecimal(price))
                    .stock(10)
                    .reservedStock(0)
                    .build();

            // when
            Product savedProduct = productRepository.save(product);

            // then
            assertThat(savedProduct).isNotNull();
            assertThat(savedProduct.getPrice()).isEqualTo(new BigDecimal(price));
        }

        @Test
        @DisplayName("실패케이스: null 상품 객체 저장")
        void save_WithNullProduct() {
            // when & then
            assertThatThrownBy(() -> productRepository.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("상품 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 상품 ID로 조회")
        void findById_Success() {
        // given
        Product product = Product.builder()
                .id(7L)
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
        @DisplayName("실패케이스: 존재하지 않는 상품 조회")
        void findById_NotFound() {
            // when
            Optional<Product> foundProduct = productRepository.findById(999L);

            // then
            assertThat(foundProduct).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 상품 ID로 조회")
        void findById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> productRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패케이스: 음수 상품 ID로 조회")
        void findById_WithNegativeId() {
            // when
            Optional<Product> foundProduct = productRepository.findById(-1L);

            // then
            assertThat(foundProduct).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryProductRepositoryTest#provideInvalidProductIds")
        @DisplayName("실패케이스: 유효하지 않은 상품 ID들로 조회")
        void findById_WithInvalidIds(Long invalidId) {
            // when
            Optional<Product> foundProduct = productRepository.findById(invalidId);

            // then
            assertThat(foundProduct).isEmpty();
        }
    }

    @Nested
    @DisplayName("페이지네이션 조회 테스트")
    class PaginationTests {
        
        @Test
        @DisplayName("성공케이스: 페이지네이션으로 상품 조회")
        void findAllWithPagination_Success() {
        // given
        for (int i = 1; i <= 15; i++) {
            Product product = Product.builder()
                    .id((long) i)
                    .name("상품" + i)
                    .price(new BigDecimal("100000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            productRepository.save(product);
        }

        // when
        List<Product> products = productRepository.findAllWithPagination(10, 0);

        // then
            assertThat(products).hasSize(10);
        }

        @Test
        @DisplayName("성공케이스: 오프셋이 있는 페이지네이션")
        void findAllWithPagination_WithOffset() {
        // given
        for (int i = 1; i <= 15; i++) {
            Product product = Product.builder()
                    .id((long) i)
                    .name("상품" + i)
                    .price(new BigDecimal("100000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            productRepository.save(product);
        }

        // when
        List<Product> products = productRepository.findAllWithPagination(5, 10);

        // then
            assertThat(products).hasSize(5);
        }

        @Test
        @DisplayName("성공케이스: 대량 상품 저장 및 조회")
        void save_AndRetrieve_LargeDataset() {
        // given
        int productCount = 1000;
        for (int i = 1; i <= productCount; i++) {
            Product product = Product.builder()
                    .name("상품" + i)
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            productRepository.save(product);
        }

        // when
        List<Product> allProducts = productRepository.findAllWithPagination(productCount, 0);

        // then
            assertThat(allProducts).hasSize(productCount);
        }

        @Test
        @DisplayName("실패케이스: 비정상적인 페이지네이션 파라미터")
        void findAll_WithInvalidPagination() {
        // given
        Product product = Product.builder()
                .id(8L)
                .name("테스트 상품")
                .price(new BigDecimal("50000"))
                .stock(10)
                .reservedStock(0)
                .build();
        productRepository.save(product);

        // when & then - 음수 limit
        List<Product> result1 = productRepository.findAllWithPagination(-1, 0);
        assertThat(result1).isEmpty();

        // when & then - 음수 offset
        List<Product> result2 = productRepository.findAllWithPagination(10, -1);
        assertThat(result2).isEmpty();

            // when & then - 0 limit
            List<Product> result3 = productRepository.findAllWithPagination(0, 0);
            assertThat(result3).isEmpty();
        }
    }

    private static Stream<Arguments> provideProductData() {
        return Stream.of(
                Arguments.of("노트북", "1200000", 50),
                Arguments.of("스마트폰", "800000", 100),
                Arguments.of("태블릿", "600000", 30),
                Arguments.of("무선이어폰", "200000", 200)
        );
    }

    private static Stream<Arguments> provideInvalidProductIds() {
        return Stream.of(
                Arguments.of(0L),
                Arguments.of(-1L),
                Arguments.of(-999L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 상품 동시 저장")
        void save_ConcurrentSaveForDifferentProducts() throws Exception {
            // given
            int numberOfProducts = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfProducts);
            AtomicInteger successCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when - 서로 다른 상품들을 동시에 저장
            for (int i = 0; i < numberOfProducts; i++) {
                final int productIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Product product = Product.builder()
                                .id((long) productIndex)
                                .name("동시성상품" + productIndex)
                                .price(new BigDecimal(String.valueOf(productIndex * 1000)))
                                .stock(productIndex * 10)
                                .reservedStock(0)
                                .build();
                        
                        productRepository.save(product);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error for product " + productIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then - 모든 상품이 성공적으로 저장되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfProducts);
            
            // 각 상품이 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfProducts; i++) {
                Optional<Product> product = productRepository.findById((long) i);
                assertThat(product).isPresent();
                assertThat(product.get().getName()).isEqualTo("동시성상품" + i);
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 상품 동시 업데이트")
        void save_ConcurrentUpdateForSameProduct() throws Exception {
            // given
            Long productId = 500L;
            Product initialProduct = Product.builder()
                    .id(productId)
                    .name("동시성 업데이트 상품")
                    .price(new BigDecimal("100000"))
                    .stock(1000)
                    .reservedStock(0)
                    .build();
            productRepository.save(initialProduct);

            int numberOfThreads = 10;
            int updatesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when - 동일한 상품을 동시에 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            Product updatedProduct = Product.builder()
                                    .id(productId)
                                    .name("동시성 업데이트 상품")
                                    .price(new BigDecimal("150000"))
                                    .stock(1000 - (threadId * updatesPerThread + j))
                                    .reservedStock(threadId * updatesPerThread + j)
                                    .build();
                            
                            productRepository.save(updatedProduct);
                            successfulUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Update error for thread " + threadId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successfulUpdates.get()).isEqualTo(numberOfThreads * updatesPerThread);
            
            // 최종 상태 확인
            Optional<Product> finalProduct = productRepository.findById(productId);
            assertThat(finalProduct).isPresent();
            assertThat(finalProduct.get().getPrice()).isEqualTo(new BigDecimal("150000"));

            executor.shutdown();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            Product baseProduct = Product.builder()
                    .id(600L)
                    .name("읽기쓰기 테스트 상품")
                    .price(new BigDecimal("50000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            productRepository.save(baseProduct);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfReaders + numberOfWriters);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 50; j++) {
                            Optional<Product> product = productRepository.findById(600L);
                            if (product.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 20; j++) {
                            Product newProduct = Product.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .name("쓰기테스트" + writerId + "_" + j)
                                    .price(new BigDecimal(String.valueOf(50000 + writerId * 1000 + j)))
                                    .stock(100)
                                    .reservedStock(0)
                                    .build();
                            
                            productRepository.save(newProduct);
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 20);
            
            // 최종 상태 확인
            Optional<Product> finalProduct = productRepository.findById(600L);
            assertThat(finalProduct).isPresent();

            executor.shutdown();
        }
    }

    private static Stream<Arguments> provideEdgeCasePrices() {
        return Stream.of(
                Arguments.of("무료 상품", "0"),
                Arguments.of("소수점 포함", "999.99"),
                Arguments.of("매우 비싼 상품", "999999999"),
                Arguments.of("최소 단위", "0.01")
        );
    }
} 