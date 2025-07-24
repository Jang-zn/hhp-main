package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.ArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetOrderListUseCase 단위 테스트")
class GetOrderListUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetOrderListUseCase getOrderListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderListUseCase = new GetOrderListUseCase(userRepositoryPort, orderRepositoryPort, cachePort);
    }

    @Nested
    @DisplayName("주문 목록 조회 성공 테스트")
    class SuccessTests {
        
        static Stream<Arguments> provideUserData() {
            return Stream.of(
                    Arguments.of(1L, "홍길동", 2),
                    Arguments.of(2L, "김철수", 1),
                    Arguments.of(3L, "이영희", 3),
                    Arguments.of(4L, "박영수", 0)
            );
        }
        
        @Test
        @DisplayName("성공케이스: 캐시에서 주문 목록 조회 성공")
        void getOrderList_Success_FromCache() {
            // given
            Long userId = 1L;
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            List<Order> cachedOrders = List.of(
                    Order.builder()
                            .id(1L)
                            .user(user)
                            .totalAmount(new BigDecimal("120000"))
                            .build(),
                    Order.builder()
                            .id(2L)
                            .user(user)
                            .totalAmount(new BigDecimal("80000"))
                            .build()
            );
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                    .thenReturn(cachedOrders);

            // when
            List<Order> result = getOrderListUseCase.execute(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTotalAmount()).isEqualTo(new BigDecimal("120000"));
            assertThat(result.get(1).getTotalAmount()).isEqualTo(new BigDecimal("80000"));
            
            verify(userRepositoryPort).findById(userId);
            verify(cachePort).get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class));
            verify(orderRepositoryPort, never()).findByUser(any(User.class)); // 캐시에서 조회되므로 DB 호출 없음
        }
        
        @Test
        @DisplayName("성공케이스: 캐시 실패 시 DB에서 주문 목록 조회 성공")
        void getOrderList_Success_FromDB_WhenCacheFails() {
            // given
            Long userId = 1L;
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            List<Order> dbOrders = List.of(
                    Order.builder()
                            .id(1L)
                            .user(user)
                            .totalAmount(new BigDecimal("150000"))
                            .build()
            );
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                    .thenThrow(new RuntimeException("캐시 오류"));
            when(orderRepositoryPort.findByUser(user)).thenReturn(dbOrders);

            // when
            List<Order> result = getOrderListUseCase.execute(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotalAmount()).isEqualTo(new BigDecimal("150000"));
            
            verify(userRepositoryPort).findById(userId);
            verify(cachePort).get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class));
            verify(orderRepositoryPort).findByUser(user); // 캐시 실패로 인한 DB 조회
        }
        
        @Test
        @DisplayName("성공케이스: 주문이 없는 사용자 조회")
        void getOrderList_Success_EmptyOrders() {
            // given
            Long userId = 1L;
            
            User user = User.builder()
                    .id(userId)
                    .name("주문 없는 사용자")
                    .build();
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                    .thenReturn(Collections.emptyList());

            // when
            List<Order> result = getOrderListUseCase.execute(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            
            verify(userRepositoryPort).findById(userId);
            verify(cachePort).get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class));
        }
        
        @ParameterizedTest
        @MethodSource("provideUserData")
        @DisplayName("성공케이스: 다양한 사용자의 주문 목록 조회")
        void getOrderList_WithDifferentUsers(Long userId, String userName, int orderCount) {
            // given
            User user = User.builder()
                    .id(userId)
                    .name(userName)
                    .build();
            
            List<Order> orders = new ArrayList<>();
            for (int i = 1; i <= orderCount; i++) {
                orders.add(Order.builder()
                        .id((long) i)
                        .user(user)
                        .totalAmount(new BigDecimal("50000").multiply(BigDecimal.valueOf(i)))
                        .build());
            }
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                    .thenReturn(orders);

            // when
            List<Order> result = getOrderListUseCase.execute(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(orderCount);
            if (orderCount > 0) {
                assertThat(result.get(0).getUser().getName()).isEqualTo(userName);
            }
            
            verify(userRepositoryPort).findById(userId);
            verify(cachePort).get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class));
        }
    }
    
    @Nested
    @DisplayName("주문 목록 조회 실패 테스트")
    class FailureTests {
        
        static Stream<Arguments> provideInvalidUserIds() {
            return Stream.of(
                    Arguments.of(null, "UserId cannot be null"),
                    Arguments.of(-1L, "UserId must be positive"),
                    Arguments.of(0L, "UserId must be positive"),
                    Arguments.of(-999L, "UserId must be positive")
            );
        }
        
        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자의 주문 목록 조회")
        void getOrderList_UserNotFound() {
            // given
            Long userId = 999L;
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> getOrderListUseCase.execute(userId))
                    .isInstanceOf(UserException.NotFound.class)
                    .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
                    
            verify(userRepositoryPort).findById(userId);
            verify(cachePort, never()).get(anyString(), any(Class.class), any(Supplier.class));
            verify(orderRepositoryPort, never()).findByUser(any(User.class));
        }
        
        @Test
        @DisplayName("실패케이스: null 사용자 ID로 조회")
        void getOrderList_WithNullUserId() {
            // when & then
            assertThatThrownBy(() -> getOrderListUseCase.execute(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UserId cannot be null");
                    
            verify(userRepositoryPort, never()).findById(any());
            verify(cachePort, never()).get(anyString(), any(Class.class), any(Supplier.class));
        }
        
        @Test
        @DisplayName("실패케이스: 음수 사용자 ID로 조회")
        void getOrderList_WithNegativeUserId() {
            // given
            Long userId = -1L;
            
            // when & then
            assertThatThrownBy(() -> getOrderListUseCase.execute(userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UserId must be positive");
                    
            verify(userRepositoryPort, never()).findById(any());
            verify(cachePort, never()).get(anyString(), any(Class.class), any(Supplier.class));
        }
        
        @Test
        @DisplayName("실패케이스: 0 사용자 ID로 조회")
        void getOrderList_WithZeroUserId() {
            // given
            Long userId = 0L;
            
            // when & then
            assertThatThrownBy(() -> getOrderListUseCase.execute(userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UserId must be positive");
                    
            verify(userRepositoryPort, never()).findById(any());
            verify(cachePort, never()).get(anyString(), any(Class.class), any(Supplier.class));
        }
        
        @ParameterizedTest
        @MethodSource("provideInvalidUserIds")
        @DisplayName("실패케이스: 다양한 비정상 사용자 ID 테스트")
        void getOrderList_WithInvalidUserIds(Long invalidUserId, String expectedMessage) {
            // when & then
            assertThatThrownBy(() -> getOrderListUseCase.execute(invalidUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
                    
            verify(userRepositoryPort, never()).findById(any());
            verify(cachePort, never()).get(anyString(), any(Class.class), any(Supplier.class));
        }
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동시성 테스트: 다수 사용자 동시 주문 목록 조회")
        void getOrderList_ConcurrentAccessForDifferentUsers() throws InterruptedException {
            // given
            int numberOfUsers = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 다수 사용자 동시 조회
            for (int i = 1; i <= numberOfUsers; i++) {
                final Long userId = (long) i;
                
                User user = User.builder()
                        .id(userId)
                        .name("사용자" + userId)
                        .build();
                
                List<Order> orders = List.of(
                        Order.builder()
                                .id(userId)
                                .user(user)
                                .totalAmount(new BigDecimal("100000"))
                                .build()
                );
                
                when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
                when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                        .thenReturn(orders);
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await(); // 동시 시작 대기
                        
                        List<Order> result = getOrderListUseCase.execute(userId);
                        
                        assertThat(result).isNotNull();
                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getUser().getName()).isEqualTo("사용자" + userId);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.println("주문 목록 조회 실패 - 사용자: " + userId + ", 오류: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 동시 실행
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 검증
            assertThat(successCount.get()).isEqualTo(numberOfUsers);
            assertThat(errorCount.get()).isEqualTo(0);
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        
        @Test
        @DisplayName("동시성 테스트: 같은 사용자의 동시 주문 목록 조회")
        void getOrderList_ConcurrentAccessForSameUser() throws InterruptedException {
            // given
            Long userId = 1L;
            int numberOfRequests = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfRequests);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfRequests);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            List<Order> orders = List.of(
                    Order.builder()
                            .id(1L)
                            .user(user)
                            .totalAmount(new BigDecimal("100000"))
                            .build(),
                    Order.builder()
                            .id(2L)
                            .user(user)
                            .totalAmount(new BigDecimal("200000"))
                            .build()
            );
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(cachePort.get(eq("user_orders_" + userId), eq(List.class), any(Supplier.class)))
                    .thenReturn(orders);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 같은 사용자에 대한 동시 조회
            for (int i = 0; i < numberOfRequests; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        List<Order> result = getOrderListUseCase.execute(userId);
                        
                        assertThat(result).isNotNull();
                        assertThat(result).hasSize(2);
                        assertThat(result.get(0).getUser().getName()).isEqualTo("테스트 사용자");
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.println("주문 목록 조회 실패: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 동시 실행
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 검증: 모든 요청이 성공해야 함 (읽기 전용이므로)
            assertThat(successCount.get()).isEqualTo(numberOfRequests);
            assertThat(errorCount.get()).isEqualTo(0);
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
    
}