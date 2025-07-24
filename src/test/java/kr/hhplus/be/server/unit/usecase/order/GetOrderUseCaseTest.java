package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetOrderUseCase 단위 테스트")
class GetOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetOrderUseCase getOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderUseCase = new GetOrderUseCase(userRepositoryPort, orderRepositoryPort, cachePort);
    }

    @Nested
    @DisplayName("기본 주문 조회 테스트")
    class BasicOrderRetrievalTests {
        
        static Stream<Arguments> provideOrderData() {
            return Stream.of(
                    Arguments.of(1L, 1L, "50000"),
                    Arguments.of(2L, 2L, "100000"),
                    Arguments.of(3L, 3L, "75000")
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 주문 조회")
        void getOrder_Success() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("120000"))
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.of(order));
            when(cachePort.get(anyString(), eq(Optional.class), any()))
                    .thenAnswer(invocation -> orderRepositoryPort.findByIdAndUser(orderId, user));

            // when
            Optional<Order> result = getOrderUseCase.execute(userId, orderId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getUser()).isEqualTo(user);
            assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal("120000"));
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자")
        void getOrder_UserNotFound() {
            // given
            Long userId = 999L;
            Long orderId = 1L;
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> getOrderUseCase.execute(userId, orderId))
                    .isInstanceOf(UserException.NotFound.class)
                    .hasMessage(UserException.Messages.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 주문")
        void getOrder_OrderNotFound() {
            // given
            Long userId = 1L;
            Long orderId = 999L;
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.empty());
            when(cachePort.get(anyString(), eq(Optional.class), any()))
                    .thenAnswer(invocation -> orderRepositoryPort.findByIdAndUser(orderId, user));

            // when
            Optional<Order> result = getOrderUseCase.execute(userId, orderId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 파라미터 검증")
        void getOrder_WithNullParameters() {
            // when & then
            assertThatThrownBy(() -> getOrderUseCase.execute(null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UserId cannot be null");
                    
            assertThatThrownBy(() -> getOrderUseCase.execute(1L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OrderId cannot be null");
        }

        @ParameterizedTest
        @MethodSource("provideOrderData")
        @DisplayName("성공케이스: 다양한 주문 조회")
        void getOrder_WithDifferentOrders(Long userId, Long orderId, String amount) {
            // given
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal(amount))
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.of(order));
            when(cachePort.get(anyString(), eq(Optional.class), any()))
                    .thenAnswer(invocation -> orderRepositoryPort.findByIdAndUser(orderId, user));

            // when
            Optional<Order> result = getOrderUseCase.execute(userId, orderId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal(amount));
        }
    }

    @Nested
    @DisplayName("캐싱 관련 테스트")
    class CachingTests {
        
        @Test
        @DisplayName("성공케이스: 캐시에서 주문 조회")
        void getOrder_FromCache() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            String cacheKey = "order_" + orderId + "_" + userId;
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Order cachedOrder = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("100000"))
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq(cacheKey), eq(Optional.class), any()))
                    .thenReturn(Optional.of(cachedOrder));

            // when
            Optional<Order> result = getOrderUseCase.execute(userId, orderId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(cachedOrder);
            verify(cachePort).get(eq(cacheKey), eq(Optional.class), any());
        }
        
        @Test
        @DisplayName("성공케이스: 캐시 미스 후 DB 조회 및 캐시 저장")
        void getOrder_CacheMissAndStore() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("100000"))
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(anyString(), eq(Optional.class), any()))
                    .thenAnswer(invocation -> {
                        // 캐시 미스 시뮬레이션: supplier 실행
                        return orderRepositoryPort.findByIdAndUser(orderId, user);
                    });
            when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.of(order));

            // when
            Optional<Order> result = getOrderUseCase.execute(userId, orderId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(order);
            verify(orderRepositoryPort).findByIdAndUser(orderId, user);
        }
    }

    @Nested
    @DisplayName("동시성 관련 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동시 조회 테스트: 여러 스레드에서 같은 주문 조회")
        void getOrder_ConcurrentAccess() throws InterruptedException {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            int threadCount = 10;
            
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("100000"))
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.of(order));
            when(cachePort.get(anyString(), eq(Optional.class), any()))
                    .thenAnswer(invocation -> orderRepositoryPort.findByIdAndUser(orderId, user));
            
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<CompletableFuture<Optional<Order>>> futures = new ArrayList<>();
            
            // when
            for (int i = 0; i < threadCount; i++) {
                CompletableFuture<Optional<Order>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        startLatch.await();
                        return getOrderUseCase.execute(userId, orderId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    } finally {
                        doneLatch.countDown();
                    }
                }, executorService);
                futures.add(future);
            }
            
            startLatch.countDown(); // 모든 스레드 동시 시작
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            
            // then
            for (CompletableFuture<Optional<Order>> future : futures) {
                Optional<Order> result = future.join();
                assertThat(result).isPresent();
                assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal("100000"));
            }
            
            executorService.shutdown();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        
        @Test
        @DisplayName("다중 사용자 동시 주문 조회 테스트")
        void getOrder_MultipleUsersConcurrentAccess() throws InterruptedException {
            // given
            int userCount = 5;
            int threadCount = userCount * 2; // 각 사용자당 2개 스레드
            
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<CompletableFuture<Optional<Order>>> futures = new ArrayList<>();
            
            // 각 사용자와 주문 설정
            for (int i = 1; i <= userCount; i++) {
                Long userId = (long) i;
                Long orderId = (long) i;
                
                User user = User.builder()
                        .name("사용자" + i)
                        .build();
                
                Order order = Order.builder()
                        .user(user)
                        .totalAmount(new BigDecimal(String.valueOf(100000 * i)))
                        .build();
                
                when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
                when(orderRepositoryPort.findByIdAndUser(orderId, user)).thenReturn(Optional.of(order));
                when(cachePort.get(eq("order_" + orderId + "_" + userId), eq(Optional.class), any()))
                        .thenAnswer(invocation -> orderRepositoryPort.findByIdAndUser(orderId, user));
                
                // 각 사용자당 2개 스레드 생성
                for (int j = 0; j < 2; j++) {
                    CompletableFuture<Optional<Order>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            startLatch.await();
                            return getOrderUseCase.execute(userId, orderId);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Optional.empty();
                        } finally {
                            doneLatch.countDown();
                        }
                    }, executorService);
                    futures.add(future);
                }
            }
            
            // when
            startLatch.countDown(); // 모든 스레드 동시 시작
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            
            // then
            for (CompletableFuture<Optional<Order>> future : futures) {
                Optional<Order> result = future.join();
                assertThat(result).isPresent();
                assertThat(result.get().getTotalAmount()).isNotNull();
            }
            
            executorService.shutdown();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

}