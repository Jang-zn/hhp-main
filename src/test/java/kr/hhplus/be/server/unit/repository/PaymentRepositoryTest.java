package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("결제 데이터 저장소 비즈니스 시나리오")
class PaymentRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private PaymentRepositoryPort paymentRepositoryPort;

    @Test
    @DisplayName("새로운 결제를 저장할 수 있다")
    void canSaveNewPayment() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .status(PaymentStatus.PENDING)
                .build();

        // When
        Payment savedPayment = paymentRepositoryPort.save(payment);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Payment foundPayment = testEntityManager.find(Payment.class, savedPayment.getId());
        assertThat(foundPayment).isNotNull();
        assertThat(foundPayment.getOrderId()).isEqualTo(1L);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("ID로 결제를 조회할 수 있다")
    void canFindPaymentById() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .status(PaymentStatus.PAID)
                .build();
        Payment savedPayment = testEntityManager.persistAndFlush(payment);
        testEntityManager.clear();

        // When
        Optional<Payment> foundPayment = paymentRepositoryPort.findById(savedPayment.getId());

        // Then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getOrderId()).isEqualTo(1L);
        assertThat(foundPayment.get().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("주문 ID로 결제를 조회할 수 있다")
    void canFindPaymentByOrderId() {
        // Given
        Long orderId = 1L;
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(orderId)
                .status(PaymentStatus.PAID)
                .build();
        testEntityManager.persistAndFlush(payment);
        testEntityManager.clear();

        // When
        List<Payment> foundPayments = paymentRepositoryPort.findByOrderId(orderId);

        // Then
        assertThat(foundPayments).isNotEmpty();
        assertThat(foundPayments.get(0).getOrderId()).isEqualTo(orderId);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("다양한 결제 상태로 저장할 수 있다")
    void canSavePaymentWithVariousStatuses(PaymentStatus status) {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .status(status)
                .build();

        // When
        Payment savedPayment = paymentRepositoryPort.save(payment);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Payment foundPayment = testEntityManager.find(Payment.class, savedPayment.getId());
        assertThat(foundPayment.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenPaymentNotFoundById() {
        // Given
        Long nonExistentId = 999L;

        // When
        Optional<Payment> foundPayment = paymentRepositoryPort.findById(nonExistentId);

        // Then
        assertThat(foundPayment).isEmpty();
    }

    @Test
    @DisplayName("null 결제 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullPayment() {
        // When & Then
        assertThatThrownBy(() -> paymentRepositoryPort.save(null))
                .isInstanceOf(Exception.class);
    }
}