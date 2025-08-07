package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.adapter.storage.jpa.PaymentJpaRepository;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
/**
 * PaymentJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: 결제 데이터 저장소의 비즈니스 로직과 데이터 무결성 보장 검증
 * How: 결제 관리 시나리오를 반영한 JPA 저장소 테스트로 구성
 */
@DisplayName("결제 데이터 저장소 비즈니스 시나리오")
class PaymentJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Payment> paymentQuery;

    private PaymentJpaRepository paymentJpaRepository;

    @BeforeEach
    void setUp() {
        paymentJpaRepository = new PaymentJpaRepository(entityManager);
    }

    @Test
    @DisplayName("새로운 결제 정보를 성공적으로 저장한다")
    void save_NewPayment_Success() {
        // given - 새로운 결제 요청 정보
        Payment newPayment = TestBuilder.PaymentBuilder.pendingPayment()
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .build();

        doNothing().when(entityManager).persist(newPayment);

        // when
        Payment savedPayment = paymentJpaRepository.save(newPayment);

        // then
        assertThat(savedPayment).isEqualTo(newPayment);
        verify(entityManager, times(1)).persist(newPayment);
    }

    @Test
    @DisplayName("기존 결제 정보를 성공적으로 업데이트한다")
    void save_ExistingPayment_Success() {
        // given - 기존 결제 정보의 업데이트
        Payment existingPayment = TestBuilder.PaymentBuilder.paidPayment()
                .id(1L)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("15000"))
                .build();

        when(entityManager.merge(existingPayment)).thenReturn(existingPayment);

        // when
        Payment savedPayment = paymentJpaRepository.save(existingPayment);

        // then
        assertThat(savedPayment).isEqualTo(existingPayment);
        verify(entityManager, times(1)).merge(existingPayment);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("모든 결제 상태에 대해 저장이 성공한다")
    void save_WithDifferentStatuses(PaymentStatus status) {
        // given - 다양한 상태의 결제 정보
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .status(status)
                .build();

        doNothing().when(entityManager).persist(payment);

        // when
        Payment savedPayment = paymentJpaRepository.save(payment);

        // then
        assertThat(savedPayment.getStatus()).isEqualTo(status);
        verify(entityManager, times(1)).persist(payment);
    }

    @Test
    @DisplayName("ID로 결제 정보를 성공적으로 조회한다")
    void findById_Success() {
        // given - 존재하는 결제 ID
        Long paymentId = 1L;
        Payment existingPayment = TestBuilder.PaymentBuilder.paidPayment()
                .id(paymentId)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .build();

        when(entityManager.find(Payment.class, paymentId)).thenReturn(existingPayment);

        // when
        Optional<Payment> foundPayment = paymentJpaRepository.findById(paymentId);

        // then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get()).isEqualTo(existingPayment);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void findById_NotFound() {
        // given - 존재하지 않는 결제 ID
        Long nonExistentId = 999L;
        when(entityManager.find(Payment.class, nonExistentId)).thenReturn(null);

        // when
        Optional<Payment> foundPayment = paymentJpaRepository.findById(nonExistentId);

        // then
        assertThat(foundPayment).isEmpty();
    }

    @Test
    @DisplayName("주문 ID로 결제 목록을 성공적으로 조회한다")
    void findByOrderId_Success() {
        // given - 한 주문에 대한 여러 결제 내역
        Long orderId = 1L;
        List<Payment> expectedPayments = Arrays.asList(
                TestBuilder.PaymentBuilder.defaultPayment().id(1L).orderId(orderId).userId(1L).amount(new BigDecimal("5000")).build(),
                TestBuilder.PaymentBuilder.defaultPayment().id(2L).orderId(orderId).userId(1L).amount(new BigDecimal("5000")).build()
        );

        when(entityManager.createQuery(anyString(), eq(Payment.class))).thenReturn(paymentQuery);
        when(paymentQuery.setParameter("orderId", orderId)).thenReturn(paymentQuery);
        when(paymentQuery.getResultList()).thenReturn(expectedPayments);

        // when
        List<Payment> payments = paymentJpaRepository.findByOrderId(orderId);

        // then
        assertThat(payments).hasSize(2);
        assertThat(payments).allMatch(p -> p.getOrderId().equals(orderId));
        verify(entityManager).createQuery("SELECT p FROM Payment p WHERE p.orderId = :orderId", Payment.class);
    }

    @Test
    @DisplayName("결제 내역이 없는 주문에 대해 빈 목록을 반환한다")
    void findByOrderId_EmptyResult() {
        // given - 결제 내역이 없는 주문
        Long orderWithoutPayment = 999L;

        when(entityManager.createQuery(anyString(), eq(Payment.class))).thenReturn(paymentQuery);
        when(paymentQuery.setParameter("orderId", orderWithoutPayment)).thenReturn(paymentQuery);
        when(paymentQuery.getResultList()).thenReturn(Arrays.asList());

        // when
        List<Payment> payments = paymentJpaRepository.findByOrderId(orderWithoutPayment);

        // then
        assertThat(payments).isEmpty();
    }

    @Test
    @DisplayName("결제 상태를 성공적으로 업데이트한다")
    void updateStatus_Success() {
        // given - 대기 상태에서 완료 상태로 변경
        Long paymentId = 1L;
        PaymentStatus newStatus = PaymentStatus.PAID;
        Payment existingPayment = TestBuilder.PaymentBuilder.pendingPayment()
                .id(paymentId)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .build();

        when(entityManager.find(Payment.class, paymentId)).thenReturn(existingPayment);
        when(entityManager.merge(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.changeStatus(newStatus);
            return payment;
        });

        // when
        Payment updatedPayment = paymentJpaRepository.updateStatus(paymentId, newStatus);

        // then
        assertThat(updatedPayment).isNotNull();
        assertThat(updatedPayment.getStatus()).isEqualTo(newStatus);
        verify(entityManager).find(Payment.class, paymentId);
        verify(entityManager).merge(existingPayment);
    }

    @Test
    @DisplayName("존재하지 않는 결제의 상태 업데이트 시 null을 반환한다")
    void updateStatus_PaymentNotFound() {
        // given - 존재하지 않는 결제 ID
        Long nonExistentPaymentId = 999L;
        PaymentStatus newStatus = PaymentStatus.PAID;

        when(entityManager.find(Payment.class, nonExistentPaymentId)).thenReturn(null);

        // when
        Payment updatedPayment = paymentJpaRepository.updateStatus(nonExistentPaymentId, newStatus);

        // then
        assertThat(updatedPayment).isNull();
        verify(entityManager).find(Payment.class, nonExistentPaymentId);
        verify(entityManager, never()).merge(any());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("모든 결제 상태로의 업데이트가 성공한다")
    void updateStatus_WithDifferentStatuses(PaymentStatus newStatus) {
        // given - 다양한 상태로의 업데이트 요청
        Long paymentId = 1L;
        Payment existingPayment = TestBuilder.PaymentBuilder.pendingPayment()
                .id(paymentId)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .build();

        when(entityManager.find(Payment.class, paymentId)).thenReturn(existingPayment);
        when(entityManager.merge(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.changeStatus(newStatus);
            return payment;
        });

        // when
        Payment updatedPayment = paymentJpaRepository.updateStatus(paymentId, newStatus);

        // then
        assertThat(updatedPayment.getStatus()).isEqualTo(newStatus);
    }

    @Test
    @DisplayName("결제 저장 중 데이터베이스 오류 시 예외가 전파된다")
    void save_PersistException() {
        // given - 데이터베이스 오류 상황
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment()
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("10000"))
                .build();

        doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(payment);

        // when & then
        assertThatThrownBy(() -> paymentJpaRepository.save(payment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 오류");
    }

    @Test
    @DisplayName("결제 조회 중 예외 발생 시 빈 결과를 반환한다")
    void findById_Exception() {
        // given - 데이터베이스 조회 오류 상황
        Long paymentId = 1L;
        when(entityManager.find(Payment.class, paymentId))
                .thenThrow(new RuntimeException("데이터베이스 오류"));

        // when
        Optional<Payment> result = paymentJpaRepository.findById(paymentId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("결제 상태 업데이트 중 데이터베이스 오류 시 예외가 전파된다")
    void throwsExceptionWhenUpdateStatusFails() {
        // given - 데이터베이스 연결 오류 상황
        Long paymentId = 1L;
        PaymentStatus newStatus = PaymentStatus.PAID;

        when(entityManager.find(Payment.class, paymentId))
                .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

        // when & then
        assertThatThrownBy(() -> paymentJpaRepository.updateStatus(paymentId, newStatus))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("데이터베이스 연결 오류");
    }

    @Test
    @DisplayName("결제 저장 중 예외 발생 시 적절한 예외가 전파된다")
    void throwsExceptionWhenSaveFails() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.pendingPayment().build();
        doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(payment);

        // When & Then
        assertThatThrownBy(() -> paymentJpaRepository.save(payment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 오류");
    }

    @Test
    @DisplayName("조회 중 예외 발생 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindByIdFails() {
        // Given
        Long id = 1L;
        when(entityManager.find(Payment.class, id))
                .thenThrow(new RuntimeException("데이터베이스 오류"));

        // When
        Optional<Payment> result = paymentJpaRepository.findById(id);

        // Then
        assertThat(result).isEmpty();
    }
}