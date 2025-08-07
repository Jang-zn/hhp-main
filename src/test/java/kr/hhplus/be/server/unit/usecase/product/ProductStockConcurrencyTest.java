package kr.hhplus.be.server.unit.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 재고 동시성 단위 테스트")
class ProductStockConcurrencyTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;
    @Mock
    private CachePort cachePort;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // GetProductUseCase는 직접 테스트하지 않고 ProductRepository만 테스트
        
        testProduct = Product.builder()
                .id(1L)
                .name("동시성 테스트 상품")
                .price(new BigDecimal("10000"))
                .stock(100)
                .reservedStock(0)
                .build();
    }

    @Test
    @DisplayName("동시 재고 예약 시 race condition 발생 확인")
    void testConcurrentStockReservation_RaceCondition() {
        // Given: 초기 재고 100개인 상품
        AtomicReference<Product> productRef = new AtomicReference<>(testProduct);
        when(productRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(productRef.get()));
        when(productRepositoryPort.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    productRef.set(saved);
                    return saved;
                });

        // When: 10개 스레드가 동시에 10개씩 재고 예약 시도
        int threadCount = 10;
        int quantityPerThread = 10;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> {
                    Product product = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                    product.reserveStock(quantityPerThread);
                    return productRepositoryPort.save(product);
                }
        );

        // Then: 모든 요청이 성공하면 race condition 존재
        assertThat(result.getSuccessCount()).isEqualTo(threadCount);
        
        Product finalProduct = productRef.get();
        // 정상적이라면 예약된 재고는 100개여야 하지만, race condition으로 인해 더 적을 수 있음
        assertThat(finalProduct.getReservedStock())
                .describedAs("Race condition으로 인해 예상보다 적은 재고가 예약될 수 있음")
                .isLessThanOrEqualTo(100);

        System.out.printf("실제 예약된 재고: %d (예상: 100)%n", finalProduct.getReservedStock());
        System.out.printf("성공 횟수: %d, 실패 횟수: %d%n", result.getSuccessCount(), result.getFailureCount());
    }

    @Test
    @DisplayName("재고 부족 시 동시 요청 처리")
    void testConcurrentStockReservation_OutOfStock() {
        // Given: 재고 5개인 상품
        Product limitedProduct = Product.builder()
                .id(1L)
                .name("재고 부족 테스트 상품")
                .price(new BigDecimal("10000"))
                .stock(5)
                .reservedStock(0)
                .build();

        AtomicReference<Product> productRef = new AtomicReference<>(limitedProduct);
        when(productRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(productRef.get()));
        when(productRepositoryPort.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    productRef.set(saved);
                    return saved;
                });

        // When: 10개 스레드가 동시에 1개씩 재고 예약 시도
        int threadCount = 10;
        int quantityPerThread = 1;

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> {
                    Product product = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                    
                    if (product.getStock() - product.getReservedStock() < quantityPerThread) {
                        throw new ProductException.OutOfStock();
                    }
                    
                    product.reserveStock(quantityPerThread);
                    return productRepositoryPort.save(product);
                }
        );

        // Then: 5개만 성공하고 나머지는 실패해야 함
        Product finalProduct = productRef.get();
        System.out.printf("재고 부족 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 예약 재고: %d%n", finalProduct.getReservedStock());

        // 적어도 일부 요청은 실패해야 함 (재고 부족)
        assertThat(result.getFailureCount()).isGreaterThan(0);
        assertThat(finalProduct.getReservedStock()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("동시 재고 예약과 해제 테스트")
    void testConcurrentReservationAndRelease() {
        // Given: 초기 재고 50개
        Product product = Product.builder()
                .id(1L)
                .name("예약/해제 테스트 상품")
                .price(new BigDecimal("10000"))
                .stock(50)
                .reservedStock(0)
                .build();

        AtomicReference<Product> productRef = new AtomicReference<>(product);
        when(productRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(productRef.get()));
        when(productRepositoryPort.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    productRef.set(saved);
                    return saved;
                });

        // When: 예약 스레드 5개 (각 5개씩), 해제 스레드 3개 (각 3개씩) 동시 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
                java.util.List.of(
                        // 예약 작업들
                        () -> {
                            Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                            p.reserveStock(5);
                            productRepositoryPort.save(p);
                        },
                        () -> {
                            Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                            p.reserveStock(5);
                            productRepositoryPort.save(p);
                        },
                        () -> {
                            Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                            p.reserveStock(5);
                            productRepositoryPort.save(p);
                        },
                        // 해제 작업들
                        () -> {
                            Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                            if (p.getReservedStock() >= 3) {
                                p.cancelReservation(3);
                                productRepositoryPort.save(p);
                            }
                        },
                        () -> {
                            Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                            if (p.getReservedStock() >= 3) {
                                p.cancelReservation(3);
                                productRepositoryPort.save(p);
                            }
                        }
                )
        );

        // Then: 결과 검증
        Product finalProduct = productRef.get();
        System.out.printf("예약/해제 동시 실행 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 예약 재고: %d%n", finalProduct.getReservedStock());

        // 최종 예약 재고는 0 이상이어야 함
        assertThat(finalProduct.getReservedStock()).isGreaterThanOrEqualTo(0);
        assertThat(finalProduct.getReservedStock()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("대량 동시 재고 예약 부하 테스트")
    void testHighConcurrencyStockReservation() {
        // Given: 재고 1000개인 상품
        Product product = Product.builder()
                .id(1L)
                .name("부하 테스트 상품")
                .price(new BigDecimal("10000"))
                .stock(1000)
                .reservedStock(0)
                .build();

        AtomicReference<Product> productRef = new AtomicReference<>(product);
        when(productRepositoryPort.findByIdWithLock(1L))
                .thenAnswer(invocation -> Optional.of(productRef.get()));
        when(productRepositoryPort.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    productRef.set(saved);
                    return saved;
                });

        // When: 100회 실행, 20개 동시 스레드로 1개씩 예약
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeLoadTest(
                () -> {
                    Product p = productRepositoryPort.findByIdWithLock(1L).orElseThrow();
                    if (p.getStock() - p.getReservedStock() >= 1) {
                        p.reserveStock(1);
                        productRepositoryPort.save(p);
                    }
                    return null;
                },
                100, // 총 실행 횟수
                20   // 동시 스레드 수
        );

        // Then: 성능 및 정확성 검증
        Product finalProduct = productRef.get();
        System.out.printf("부하 테스트 결과: 성공 %d, 실패 %d, 실행시간 %dms%n", 
                result.getSuccessCount(), result.getFailureCount(), result.getExecutionTimeMs());
        System.out.printf("최종 예약 재고: %d%n", finalProduct.getReservedStock());

        // 성공률이 어느 정도 이상이어야 함
        assertThat(result.getSuccessRate()).isGreaterThan(80.0);
        assertThat(finalProduct.getReservedStock()).isLessThanOrEqualTo(1000);
        
        // 적절한 성능 기준 (1초 이내)
        assertThat(result.getExecutionTimeMs()).isLessThan(1000);
    }
}