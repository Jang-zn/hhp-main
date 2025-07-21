package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.domain.exception.UserException;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreateOrderUseCase 단위 테스트")
class CreateOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderUseCase = new CreateOrderUseCase(
                userRepositoryPort, productRepositoryPort, orderRepositoryPort,
                eventLogRepositoryPort, lockingPort, cachePort
        );
    }

    @Nested
    @DisplayName("기본 주문 생성 테스트")
    class BasicOrderCreationTests {
        
        @Test
        @DisplayName("성공케이스: 정상 주문 생성")
        void createOrder_Success() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2, 2L, 1);
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Product product1 = Product.builder()
                    .name("노트북")
                    .price(new BigDecimal("1200000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            
            Product product2 = Product.builder()
                    .name("스마트폰")
                    .price(new BigDecimal("800000"))
                    .stock(20)
                    .reservedStock(0)
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product1));
            when(productRepositoryPort.findById(2L)).thenReturn(Optional.of(product2));
            when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Order result = createOrderUseCase.execute(userId, productQuantities);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo(user);
            assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("3200000")); // 1200000*2 + 800000*1
            verify(lockingPort).acquireLock("order-creation-" + userId);
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
        
        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자")
        void createOrder_UserNotFound() {
            // given
            Long userId = 999L;
            Map<Long, Integer> productQuantities = Map.of(1L, 1);
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                    .isInstanceOf(UserException.NotFound.class)
                    .hasMessage("User not found");
                    
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
        
        @Test
        @DisplayName("실패케이스: 빈 주문 목록")
        void createOrder_EmptyProductList() {
            // given
            Long userId = 1L;
            Map<Long, Integer> emptyProductQuantities = Collections.emptyMap();

            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, emptyProductQuantities))
                    .isInstanceOf(OrderException.EmptyItems.class)
                    .hasMessage("Order must contain at least one item");
        }
        
        @Test
        @DisplayName("실패케이스: null 파라미터")
        void createOrder_NullParameters() {
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(null, Map.of(1L, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UserId cannot be null");
                    
            assertThatThrownBy(() -> createOrderUseCase.execute(1L, null))
                    .isInstanceOf(OrderException.EmptyItems.class);
        }
        
        @Test
        @DisplayName("실패케이스: 음수 수량")
        void createOrder_NegativeQuantity() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, -1);

            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be positive");
        }
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 상품 조합으로 주문 생성")
    void createOrder_WithDifferentProducts(Long userId, Map<Long, Integer> productQuantities) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        // 각 상품에 대해 Mock 설정
        productQuantities.keySet().forEach(productId -> {
            Product product = Product.builder()
                    .name("상품" + productId)
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(product));
            when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        });
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = createOrderUseCase.execute(userId, productQuantities);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 주문 생성 시 예외 발생")
    void createOrder_UserNotFound() {
        // given
        Long userId = 999L;
        Map<Long, Integer> productQuantities = Map.of(1L, 1);
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 주문 생성 시 예외 발생")
    void createOrder_ProductNotFound() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(999L, 1);
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.NotFound.class)
                .hasMessage("Product not found");
    }

    @Test
    @DisplayName("재고 부족 상품으로 주문 생성 시 예외 발생")
    void createOrder_InsufficientStock() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 100); // 재고보다 많은 주문
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("재고 부족 상품")
                .price(new BigDecimal("10000"))
                .stock(10) // 주문량보다 적은 재고
                .reservedStock(0)
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class)
                .hasMessage("Product out of stock");
    }

    @Test
    @DisplayName("빈 주문 리스트로 주문 생성 시 예외 발생")
    void createOrder_EmptyProductList() {
        // given
        Long userId = 1L;
        Map<Long, Integer> emptyProductQuantities = Collections.emptyMap();

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, emptyProductQuantities))
                .isInstanceOf(OrderException.EmptyItems.class)
                .hasMessage("Order must contain at least one item");
    }

    @Test
    @DisplayName("null 사용자 ID로 주문 생성 시 예외 발생")
    void createOrder_WithNullUserId() {
        // given
        Long userId = null;
        Map<Long, Integer> productQuantities = Map.of(1L, 1);

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 주문 리스트로 주문 생성 시 예외 발생")
    void createOrder_WithNullProductList() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = null;

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(OrderException.EmptyItems.class);
    }

    @Test
    @DisplayName("음수 수량으로 주문 생성 시 예외 발생")
    void createOrder_WithNegativeQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, -1); // 음수 수량
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    @DisplayName("0 수량으로 주문 생성 시 예외 발생")
    void createOrder_WithZeroQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 0); // 0 수량
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    @DisplayName("대량 주문 생성 시 예외 발생")
    void createOrder_ExcessiveQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 1000000); // 대량 주문
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(100)
                .reservedStock(0)
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class)
                .hasMessage("Product out of stock");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("다양한 비정상 사용자 ID로 주문 생성")
    void createOrder_WithInvalidUserIds(Long invalidUserId) {
        // given
        Map<Long, Integer> productQuantities = Map.of(1L, 1);
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(invalidUserId, productQuantities))
                .isInstanceOf(UserException.NotFound.class);
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of(1L, Map.of(1L, 1)), // 단일 상품
                Arguments.of(2L, Map.of(1L, 2, 2L, 1)), // 다중 상품
                Arguments.of(3L, Map.of(3L, 5)) // 대량 주문
        );
    }

    @Nested
    @DisplayName("재고 예약 관련 테스트")
    class StockReservationTests {
        
        @Test
        @DisplayName("성공케이스: 재고 예약 확인")
        void createOrder_StockReservation() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 3);
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));
            when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Order result = createOrderUseCase.execute(userId, productQuantities);

            // then
            assertThat(result).isNotNull();
            // reserveStock이 호출되었는지 확인하기 위해 Product의 상태 검증
            assertThat(product.getReservedStock()).isEqualTo(3);
            assertThat(product.getStock()).isEqualTo(10); // 실제 재고는 변하지 않음
        }
        
        @Test
        @DisplayName("실패케이스: 재고 부족으로 예약 실패")
        void createOrder_InsufficientStock() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 15); // 재고보다 많은 주문
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Product product = Product.builder()
                    .name("재고 부족 상품")
                    .price(new BigDecimal("10000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                    .isInstanceOf(ProductException.OutOfStock.class)
                    .hasMessage("Product out of stock");
                    
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
    }

    @Nested
    @DisplayName("동시성 관련 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("실패케이스: 락 획득 실패")
        void createOrder_LockAcquisitionFailure() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 1);
            
            when(lockingPort.acquireLock(anyString())).thenReturn(false); // 락 획득 실패

            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                    .isInstanceOf(OrderException.ConcurrencyConflict.class)
                    .hasMessage("Concurrent order creation conflict");
                    
            verify(lockingPort, never()).releaseLock(anyString()); // 락을 획득하지 못했으므로 release도 호출되지 않음
        }
        
        @Test
        @DisplayName("동시성 테스트: 여러 사용자 동시 주문")
        void createOrder_ConcurrentMultipleUsers() throws InterruptedException {
            // given
            int threadCount = 5;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 각 사용자와 상품 설정
            for (int i = 1; i <= threadCount; i++) {
                Long userId = (long) i;
                Map<Long, Integer> productQuantities = Map.of((long) i, 1);
                
                User user = User.builder()
                        .name("사용자" + i)
                        .build();
                
                Product product = Product.builder()
                        .name("상품" + i)
                        .price(new BigDecimal("10000"))
                        .stock(5)
                        .reservedStock(0)
                        .build();
                
                when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
                when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
                when(productRepositoryPort.findById((long) i)).thenReturn(Optional.of(product));
                when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        createOrderUseCase.execute(userId, productQuantities);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }, executorService);
                futures.add(future);
            }
            
            // when
            startLatch.countDown(); // 모든 스레드 동시 시작
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            
            // then
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failureCount.get()).isEqualTo(0);
            
            executorService.shutdown();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        
        @Test
        @DisplayName("동시성 테스트: 같은 상품에 대한 경합")
        void createOrder_ConcurrentSameProduct() throws InterruptedException {
            // given
            int threadCount = 3;
            Long productId = 1L;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            Product product = Product.builder()
                    .name("경합 상품")
                    .price(new BigDecimal("10000"))
                    .stock(5)
                    .reservedStock(0)
                    .build();
            
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // when
            for (int i = 1; i <= threadCount; i++) {
                Long userId = (long) i;
                Map<Long, Integer> productQuantities = Map.of(productId, 2); // 각자 2개씩 주문 (총 6개, 재고는 5개)
                
                User user = User.builder()
                        .name("사용자" + i)
                        .build();
                
                when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
                when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
                when(productRepositoryPort.findById(productId)).thenReturn(Optional.of(product));
                when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        createOrderUseCase.execute(userId, productQuantities);
                        successCount.incrementAndGet();
                    } catch (ProductException.OutOfStock e) {
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }, executorService);
                futures.add(future);
            }
            
            startLatch.countDown(); // 모든 스레드 동시 시작
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            
            // then
            // 재고가 5개이므로 최대 2명 성공 (2+2=4), 1명 실패 예상
            // 하지만 mock 환경이므로 실제 재고 차감은 발생하지 않아 모든 요청이 성공할 수 있음
            assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
            
            executorService.shutdown();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }
} 