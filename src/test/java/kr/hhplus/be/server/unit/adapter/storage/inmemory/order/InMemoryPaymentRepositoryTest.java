package kr.hhplus.be.server.unit.adapter.storage.inmemory.order;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryPaymentRepository;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.exception.PaymentException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryPaymentRepository 비즈니스 시나리오 테스트
 * 
 * Why: 결제 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 결제 처리 시나리오를 반영한 테스트로 구성
 */
@DisplayName("결제 저장소 비즈니스 시나리오")
class InMemoryPaymentRepositoryTest {

    private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
    }

    @Test
    @DisplayName("고객의 결제 정보를 저장할 수 있다")
    void canSaveCustomerPaymentInfo() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment().build();

        // When
        Payment saved = paymentRepository.save(payment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getOrderId()).isEqualTo(payment.getOrderId());
        assertThat(saved.getUserId()).isEqualTo(payment.getUserId());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualTo(payment.getAmount());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("모든 결제 상태에 대해 저장할 수 있다")
    void canSavePaymentWithAllStatuses(PaymentStatus status) {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment()
            .status(status).build();

        // When
        Payment saved = paymentRepository.save(payment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("영액 결제를 저장할 수 있다")
    void canSaveZeroAmountPayment() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment()
            .amount(BigDecimal.ZERO)
            .status(PaymentStatus.PAID)
            .build();

        // When
        Payment saved = paymentRepository.save(payment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("결제 ID로 결제 정보를 조회할 수 있다")
    void canFindPaymentById() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.paidPayment().build();
        Payment saved = paymentRepository.save(payment);

        // When
        Optional<Payment> found = paymentRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(found.get().getAmount()).isEqualTo(payment.getAmount());
    }

    @Test
    @DisplayName("존재하지 않는 결제 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenPaymentNotFound() {
        // When
        Optional<Payment> found = paymentRepository.findById(999L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null ID로 결제 조회 시 예외가 발생한다")
    void throwsExceptionWhenFindingWithNullId() {
        // When & Then
        assertThatThrownBy(() -> paymentRepository.findById(null))
            .isInstanceOf(PaymentException.PaymentIdCannotBeNull.class);
    }

    @Test
    @DisplayName("음수 ID로 결제 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindingWithNegativeId() {
        // When
        Optional<Payment> found = paymentRepository.findById(-1L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 고객의 결제를 동시에 처리할 수 있다")
    void canHandleSimultaneousPaymentsFromDifferentCustomers() {
        // Given
        int numberOfPayments = 20;

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfPayments, () -> {
                Payment payment = TestBuilder.PaymentBuilder.pendingPayment()
                    .id(System.nanoTime() % 100000) // 고유 ID
                    .userId(System.nanoTime() % 1000) // 고유 사용자 ID
                    .orderId(System.nanoTime() % 10000) // 고유 주문 ID
                    .amount(new BigDecimal(String.valueOf((System.nanoTime() % 1000) * 100)))
                    .build();
                return paymentRepository.save(payment);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfPayments);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 결제의 상태 업데이트를 동시에 처리할 수 있다")
    void canHandleSimultaneousUpdatesOfSamePayment() {
        // Given
        Payment initialPayment = TestBuilder.PaymentBuilder.pendingPayment()
            .id(500L).build();
        paymentRepository.save(initialPayment);

        int numberOfUpdates = 10;

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfUpdates, () -> {
                Payment updatedPayment = TestBuilder.PaymentBuilder.pendingPayment()
                    .id(500L)
                    .status(Math.random() > 0.5 ? PaymentStatus.PAID : PaymentStatus.FAILED)
                    .amount(new BigDecimal(String.valueOf(100000 + Math.random() * 50000)))
                    .build();
                return paymentRepository.save(updatedPayment);
            });

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfUpdates);
        
        Optional<Payment> finalPayment = paymentRepository.findById(500L);
        assertThat(finalPayment).isPresent();
        assertThat(finalPayment.get().getStatus()).isIn(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("결제 조회와 저장이 동시에 실행될 수 있다")
    void canReadAndWritePaymentsConcurrently() {
        // Given
        Payment basePayment = TestBuilder.PaymentBuilder.paidPayment()
            .id(600L).build();
        paymentRepository.save(basePayment);

        // When - 읽기와 쓰기 작업 동시 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeMultipleTasks(List.of(
                // 읽기 작업
                () -> {
                    for (int i = 0; i < 50; i++) {
                        paymentRepository.findById(600L);
                        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                },
                // 쓰기 작업
                () -> {
                    for (int i = 0; i < 25; i++) {
                        Payment newPayment = TestBuilder.PaymentBuilder.pendingPayment()
                            .id((long)(700 + i))
                            .userId((long)(700 + i))
                            .orderId((long)(700 + i))
                            .amount(new BigDecimal(String.valueOf(50000 + i * 1000)))
                            .build();
                        paymentRepository.save(newPayment);
                        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            ));

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        Optional<Payment> finalPayment = paymentRepository.findById(600L);
        assertThat(finalPayment).isPresent();
    }

    @ParameterizedTest
    @MethodSource("provideDiversePaymentData")
    @DisplayName("다양한 결제 데이터를 저장할 수 있다")
    void canSaveDiversePaymentData(String customerName, String amount, PaymentStatus status) {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment()
            .amount(new BigDecimal(amount))
            .status(status)
            .build();

        // When
        Payment saved = paymentRepository.save(payment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getAmount()).isEqualTo(new BigDecimal(amount));
        assertThat(saved.getStatus()).isEqualTo(status);
    }

    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideDiversePaymentData() {
        return Stream.of(
            Arguments.of("홍길동", "100000", PaymentStatus.PENDING),
            Arguments.of("김철수", "250000", PaymentStatus.PAID),
            Arguments.of("이영희", "75000", PaymentStatus.FAILED),
            Arguments.of("박영수", "0", PaymentStatus.PAID),
            Arguments.of("최민정", "999999", PaymentStatus.PENDING)
        );
    }
}