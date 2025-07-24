package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryOrderRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.OrderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryOrderRepository 단위 테스트")
class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
    }

    @Nested
    @DisplayName("주문 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 주문 저장")
        void save_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .user(user)
                .totalAmount(new BigDecimal("120000"))
                .build();

        // when
        Order savedOrder = orderRepository.save(order);

        // then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getUser()).isEqualTo(user);
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryOrderRepositoryTest#provideOrderData")
        @DisplayName("성공케이스: 다양한 주문 데이터로 저장")
        void save_WithDifferentOrderData(String userName, String totalAmount) {
            // given
            User user = User.builder()
                    .id(2L)
                    .name(userName)
                    .build();
            
            Order order = Order.builder()
                    .id(2L)
                    .user(user)
                    .totalAmount(new BigDecimal(totalAmount))
                    .build();

            // when
            Order savedOrder = orderRepository.save(order);

            // then
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getUser().getName()).isEqualTo(userName);
            assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal(totalAmount));
        }

        @Test
        @DisplayName("성공케이스: 영액 주문 저장")
        void save_ZeroAmountOrder() {
            // given
            User user = User.builder()
                    .id(3L)
                    .name("영액 주문 사용자")
                    .build();
            
            Order order = Order.builder()
                    .id(3L)
                    .user(user)
                    .totalAmount(BigDecimal.ZERO)
                    .build();

            // when
            Order savedOrder = orderRepository.save(order);

            // then
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("성공케이스: 대금액 주문 저장")
        void save_LargeAmountOrder() {
            // given
            User user = User.builder()
                    .id(4L)
                    .name("대금액 주문 사용자")
                    .build();
            
            Order order = Order.builder()
                    .id(4L)
                    .user(user)
                    .totalAmount(new BigDecimal("999999999"))
                    .build();

            // when
            Order savedOrder = orderRepository.save(order);

            // then
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("999999999"));
        }
    }

    @Nested
    @DisplayName("주문 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 주문 ID로 조회")
        void findById_Success() {
        // given
        User user = User.builder()
                .id(5L)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(5L)
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .build();
        Order savedOrder = orderRepository.save(order);

        // when
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());

        // then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUser()).isEqualTo(user);
            assertThat(foundOrder.get().getTotalAmount()).isEqualTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 주문 조회")
        void findById_NotFound() {
            // when
            Optional<Order> foundOrder = orderRepository.findById(999L);

            // then
            assertThat(foundOrder).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null ID로 주문 조회")
        void findById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> orderRepository.findById(null))
                    .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
        }

        @Test
        @DisplayName("실패케이스: 음수 ID로 주문 조회")
        void findById_WithNegativeId() {
            // when
            Optional<Order> foundOrder = orderRepository.findById(-1L);

            // then
            assertThat(foundOrder).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 주문 동시 생성")
        void save_ConcurrentSaveForDifferentOrders() throws Exception {
            // given
            int numberOfOrders = 20;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfOrders);
            AtomicInteger successCount = new AtomicInteger(0);

            // when - 서로 다른 주문들을 동시에 생성
            for (int i = 0; i < numberOfOrders; i++) {
                final int orderIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .id((long) orderIndex)
                                .name("사용자" + orderIndex)
                                .build();
                        
                        Order order = Order.builder()
                                .id((long) orderIndex)
                                .user(user)
                                .totalAmount(new BigDecimal(String.valueOf(orderIndex * 1000)))
                                .build();
                        
                        orderRepository.save(order);
                        Thread.sleep(1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error for order " + orderIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();
            // then - 모든 주문이 성공적으로 생성되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfOrders);
            
            // 각 주문이 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfOrders; i++) {
                Optional<Order> order = orderRepository.findById((long) i);
                assertThat(order).isPresent();
                assertThat(order.get().getTotalAmount()).isEqualTo(new BigDecimal(String.valueOf(i * 1000)));
            }
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 여러 주문 동시 생성")
        void save_ConcurrentOrdersForSameUser() throws Exception {
            // given
            Long userId = 500L;
            User user = User.builder()
                    .id(userId)
                    .name("동시성 테스트 사용자")
                    .build();

            int numberOfOrders = 10;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfOrders);
            AtomicInteger successfulOrders = new AtomicInteger(0);
            // when - 동일한 사용자가 여러 주문을 동시에 생성
            for (int i = 0; i < numberOfOrders; i++) {
                final int orderIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Order order = Order.builder()
                                .id((long) (500 + orderIndex))
                                .user(user)
                                .totalAmount(new BigDecimal(String.valueOf(orderIndex * 5000)))
                                .build();
                        
                        orderRepository.save(order);
                        Thread.sleep(1);
                        successfulOrders.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Order error for order " + orderIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulOrders.get()).isEqualTo(numberOfOrders);
            
            // 사용자의 주문 목록 확인
            List<Order> userOrders = orderRepository.findByUser(user);
            assertThat(userOrders).hasSize(numberOfOrders);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            User testUser = User.builder()
                    .id(600L)
                    .name("읽기쓰기 테스트 사용자")
                    .build();
            
            // 초기 주문 생성
            Order initialOrder = Order.builder()
                    .id(600L)
                    .user(testUser)
                    .totalAmount(new BigDecimal("100000"))
                    .build();
            orderRepository.save(initialOrder);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);
            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            Optional<Order> order = orderRepository.findById(600L);
                            if (order.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            Order newOrder = Order.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .user(testUser)
                                    .totalAmount(new BigDecimal(String.valueOf(50000 + writerId * 1000 + j)))
                                    .build();
                            
                            orderRepository.save(newOrder);
                            Thread.sleep(1);
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 10);
            
            // 최종 상태 확인
            List<Order> userOrders = orderRepository.findByUser(testUser);
            assertThat(userOrders.size()).isGreaterThan(1);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "75000")
        );
    }
}