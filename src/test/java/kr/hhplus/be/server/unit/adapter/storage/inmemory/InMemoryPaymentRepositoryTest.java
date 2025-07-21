package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryPaymentRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryPaymentRepository 단위 테스트")
class InMemoryPaymentRepositoryTest {

    private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
    }

    @Nested
    @DisplayName("결제 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 결제 저장")
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
        
        Payment payment = Payment.builder()
                .id(1L)
                .order(order)
                .user(user)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("120000"))
                .build();

        // when
        Payment savedPayment = paymentRepository.save(payment);

        // then
        assertThat(savedPayment).isNotNull();
        assertThat(savedPayment.getOrder()).isEqualTo(order);
        assertThat(savedPayment.getUser()).isEqualTo(user);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedPayment.getAmount()).isEqualTo(new BigDecimal("120000"));
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryPaymentRepositoryTest#providePaymentData")
        @DisplayName("성공케이스: 다양한 결제 데이터로 저장")
        void save_WithDifferentPaymentData(String userName, String amount, PaymentStatus status) {
            // given
            User user = User.builder()
                    .id(2L)
                    .name(userName)
                    .build();
            
            Order order = Order.builder()
                    .id(2L)
                    .user(user)
                    .totalAmount(new BigDecimal(amount))
                    .build();
            
            Payment payment = Payment.builder()
                    .id(2L)
                    .order(order)
                    .user(user)
                    .status(status)
                    .amount(new BigDecimal(amount))
                    .build();

            // when
            Payment savedPayment = paymentRepository.save(payment);

            // then
            assertThat(savedPayment).isNotNull();
            assertThat(savedPayment.getUser().getName()).isEqualTo(userName);
            assertThat(savedPayment.getAmount()).isEqualTo(new BigDecimal(amount));
            assertThat(savedPayment.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("성공케이스: 영액 결제 저장")
        void save_ZeroAmountPayment() {
            // given
            User user = User.builder()
                    .id(3L)
                    .name("영액 결제 사용자")
                    .build();
            
            Order order = Order.builder()
                    .id(3L)
                    .user(user)
                    .totalAmount(BigDecimal.ZERO)
                    .build();
            
            Payment payment = Payment.builder()
                    .id(3L)
                    .order(order)
                    .user(user)
                    .status(PaymentStatus.PAID)
                    .amount(BigDecimal.ZERO)
                    .build();

            // when
            Payment savedPayment = paymentRepository.save(payment);

            // then
            assertThat(savedPayment).isNotNull();
            assertThat(savedPayment.getAmount()).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("결제 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 결제 ID로 조회")
        void findById_Success() {
        // given
        User user = User.builder()
                .id(4L)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(4L)
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .build();
        
        Payment payment = Payment.builder()
                .id(4L)
                .order(order)
                .user(user)
                .status(PaymentStatus.PAID)
                .amount(new BigDecimal("50000"))
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        // when
        Optional<Payment> foundPayment = paymentRepository.findById(savedPayment.getId());

        // then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(foundPayment.get().getAmount()).isEqualTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 결제 조회")
        void findById_NotFound() {
            // when
            Optional<Payment> foundPayment = paymentRepository.findById(999L);

            // then
            assertThat(foundPayment).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null ID로 결제 조회")
        void findById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> paymentRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Payment ID cannot be null");
        }

        @Test
        @DisplayName("실패케이스: 음수 ID로 결제 조회")
        void findById_WithNegativeId() {
            // when
            Optional<Payment> foundPayment = paymentRepository.findById(-1L);

            // then
            assertThat(foundPayment).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 결제 동시 생성")
        void save_ConcurrentSaveForDifferentPayments() throws Exception {
            // given
            int numberOfPayments = 20;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfPayments);
            AtomicInteger successCount = new AtomicInteger(0);

            // when - 서로 다른 결제들을 동시에 생성
            for (int i = 0; i < numberOfPayments; i++) {
                final int paymentIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .id((long) paymentIndex)
                                .name("동시성사용자" + paymentIndex)
                                .build();
                        
                        Order order = Order.builder()
                                .id((long) paymentIndex)
                                .user(user)
                                .totalAmount(new BigDecimal(String.valueOf(paymentIndex * 1000)))
                                .build();
                        
                        Payment payment = Payment.builder()
                                .id((long) paymentIndex)
                                .order(order)
                                .user(user)
                                .status(PaymentStatus.PENDING)
                                .amount(new BigDecimal(String.valueOf(paymentIndex * 1000)))
                                .build();
                        
                        paymentRepository.save(payment);
                        Thread.sleep(1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error for payment " + paymentIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then - 모든 결제가 성공적으로 생성되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfPayments);
            
            // 각 결제가 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfPayments; i++) {
                Optional<Payment> payment = paymentRepository.findById((long) i);
                assertThat(payment).isPresent();
                assertThat(payment.get().getAmount()).isEqualTo(new BigDecimal(String.valueOf(i * 1000)));
            }
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 결제 동시 업데이트")
        void save_ConcurrentUpdateForSamePayment() throws Exception {
            // given
            Long paymentId = 500L;
            User user = User.builder().id(500L).name("동시성 사용자").build();
            Order order = Order.builder().id(500L).user(user).totalAmount(new BigDecimal("100000")).build();
            
            Payment initialPayment = Payment.builder()
                    .id(paymentId)
                    .order(order)
                    .user(user)
                    .status(PaymentStatus.PENDING)
                    .amount(new BigDecimal("100000"))
                    .build();
            paymentRepository.save(initialPayment);

            int numberOfThreads = 5;
            int updatesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            // when - 동일한 결제를 동시에 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            Payment updatedPayment = Payment.builder()
                                    .id(paymentId)
                                    .order(order)
                                    .user(user)
                                    .status(threadId % 2 == 0 ? PaymentStatus.PAID : PaymentStatus.FAILED)
                                    .amount(new BigDecimal(String.valueOf(100000 + threadId * 1000 + j)))
                                    .build();
                            
                            paymentRepository.save(updatedPayment);
                            Thread.sleep(1);
                            successfulUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Update error for thread " + threadId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulUpdates.get()).isEqualTo(numberOfThreads * updatesPerThread);
            
            // 최종 상태 확인
            Optional<Payment> finalPayment = paymentRepository.findById(paymentId);
            assertThat(finalPayment).isPresent();
            assertThat(finalPayment.get().getStatus()).isIn(PaymentStatus.PAID, PaymentStatus.FAILED);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            User baseUser = User.builder().id(600L).name("읽기쓰기 테스트 사용자").build();
            Order baseOrder = Order.builder().id(600L).user(baseUser).totalAmount(new BigDecimal("50000")).build();
            
            Payment basePayment = Payment.builder()
                    .id(600L)
                    .order(baseOrder)
                    .user(baseUser)
                    .status(PaymentStatus.PAID)
                    .amount(new BigDecimal("50000"))
                    .build();
            paymentRepository.save(basePayment);

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
                            Optional<Payment> payment = paymentRepository.findById(600L);
                            if (payment.isPresent()) {
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
                            User newUser = User.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .name("쓰기테스트" + writerId + "_" + j)
                                    .build();
                            
                            Order newOrder = Order.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .user(newUser)
                                    .totalAmount(new BigDecimal(String.valueOf(50000 + writerId * 1000 + j)))
                                    .build();
                            
                            Payment newPayment = Payment.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .order(newOrder)
                                    .user(newUser)
                                    .status(PaymentStatus.PENDING)
                                    .amount(new BigDecimal(String.valueOf(50000 + writerId * 1000 + j)))
                                    .build();
                            
                            paymentRepository.save(newPayment);
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
            Optional<Payment> finalPayment = paymentRepository.findById(600L);
            assertThat(finalPayment).isPresent();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of("홍길동", "100000", PaymentStatus.PENDING),
                Arguments.of("김철수", "250000", PaymentStatus.PAID),
                Arguments.of("이영희", "75000", PaymentStatus.FAILED)
        );
    }
}