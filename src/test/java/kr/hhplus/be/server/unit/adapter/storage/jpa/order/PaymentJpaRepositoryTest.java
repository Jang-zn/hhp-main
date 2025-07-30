package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.PaymentJpaRepository;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentJpaRepository 단위 테스트")
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

    @Nested
    @DisplayName("결제 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 결제 저장")
        void save_NewPayment_Success() {
            // given
            Order order = Order.builder().id(1L).build();
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(new BigDecimal("10000"))
                    .status(PaymentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(payment);

            // when
            Payment savedPayment = paymentJpaRepository.save(payment);

            // then
            assertThat(savedPayment).isEqualTo(payment);
            verify(entityManager, times(1)).persist(payment);
        }

        @Test
        @DisplayName("성공케이스: 기존 결제 업데이트")
        void save_ExistingPayment_Success() {
            // given
            Order order = Order.builder().id(1L).build();
            Payment payment = Payment.builder()
                    .id(1L)
                    .order(order)
                    .amount(new BigDecimal("15000"))
                    .status(PaymentStatus.PAID)
                    .build();

            when(entityManager.merge(payment)).thenReturn(payment);

            // when
            Payment savedPayment = paymentJpaRepository.save(payment);

            // then
            assertThat(savedPayment).isEqualTo(payment);
            verify(entityManager, times(1)).merge(payment);
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        @DisplayName("성공케이스: 다양한 상태의 결제 저장")
        void save_WithDifferentStatuses(PaymentStatus status) {
            // given
            Order order = Order.builder().id(1L).build();
            Payment payment = Payment.builder()
                    .order(order)
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
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 결제 조회")
        void findById_Success() {
            // given
            Long id = 1L;
            Order order = Order.builder().id(1L).build();
            Payment expectedPayment = Payment.builder()
                    .id(id)
                    .order(order)
                    .amount(new BigDecimal("10000"))
                    .status(PaymentStatus.PAID)
                    .build();

            when(entityManager.find(Payment.class, id)).thenReturn(expectedPayment);

            // when
            Optional<Payment> foundPayment = paymentJpaRepository.findById(id);

            // then
            assertThat(foundPayment).isPresent();
            assertThat(foundPayment.get()).isEqualTo(expectedPayment);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long id = 999L;
            when(entityManager.find(Payment.class, id)).thenReturn(null);

            // when
            Optional<Payment> foundPayment = paymentJpaRepository.findById(id);

            // then
            assertThat(foundPayment).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문 ID로 조회 테스트")
    class FindByOrderIdTests {

        @Test
        @DisplayName("성공케이스: 주문 ID로 결제 목록 조회")
        void findByOrderId_Success() {
            // given
            Long orderId = 1L;
            Order order = Order.builder().id(orderId).build();
            List<Payment> expectedPayments = Arrays.asList(
                    Payment.builder().id(1L).order(order).amount(new BigDecimal("5000")).build(),
                    Payment.builder().id(2L).order(order).amount(new BigDecimal("5000")).build()
            );

            when(entityManager.createQuery(anyString(), eq(Payment.class))).thenReturn(paymentQuery);
            when(paymentQuery.setParameter("orderId", orderId)).thenReturn(paymentQuery);
            when(paymentQuery.getResultList()).thenReturn(expectedPayments);

            // when
            List<Payment> payments = paymentJpaRepository.findByOrderId(orderId);

            // then
            assertThat(payments).hasSize(2);
            assertThat(payments).allMatch(p -> p.getOrder().getId().equals(orderId));
            verify(entityManager).createQuery("SELECT p FROM Payment p WHERE p.order.id = :orderId", Payment.class);
        }

        @Test
        @DisplayName("성공케이스: 결제가 없는 주문")
        void findByOrderId_EmptyResult() {
            // given
            Long orderId = 999L;

            when(entityManager.createQuery(anyString(), eq(Payment.class))).thenReturn(paymentQuery);
            when(paymentQuery.setParameter("orderId", orderId)).thenReturn(paymentQuery);
            when(paymentQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<Payment> payments = paymentJpaRepository.findByOrderId(orderId);

            // then
            assertThat(payments).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태 업데이트 테스트")
    class UpdateStatusTests {

        @Test
        @DisplayName("성공케이스: 결제 상태 업데이트")
        void updateStatus_Success() {
            // given
            Long paymentId = 1L;
            PaymentStatus newStatus = PaymentStatus.PAID;
            Order order = Order.builder().id(1L).build();
            Payment existingPayment = Payment.builder()
                    .id(paymentId)
                    .order(order)
                    .amount(new BigDecimal("10000"))
                    .status(PaymentStatus.PENDING)
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
        @DisplayName("실패케이스: 존재하지 않는 결제 상태 업데이트")
        void updateStatus_PaymentNotFound() {
            // given
            Long paymentId = 999L;
            PaymentStatus newStatus = PaymentStatus.PAID;

            when(entityManager.find(Payment.class, paymentId)).thenReturn(null);

            // when
            Payment updatedPayment = paymentJpaRepository.updateStatus(paymentId, newStatus);

            // then
            assertThat(updatedPayment).isNull();
            verify(entityManager).find(Payment.class, paymentId);
            verify(entityManager, never()).merge(any());
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        @DisplayName("성공케이스: 다양한 상태로 업데이트")
        void updateStatus_WithDifferentStatuses(PaymentStatus newStatus) {
            // given
            Long paymentId = 1L;
            Order order = Order.builder().id(1L).build();
            Payment existingPayment = Payment.builder()
                    .id(paymentId)
                    .order(order)
                    .amount(new BigDecimal("10000"))
                    .status(PaymentStatus.PENDING)
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
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            Order order = Order.builder().id(1L).build();
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(new BigDecimal("10000"))
                    .status(PaymentStatus.PENDING)
                    .build();

            doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(payment);

            // when & then
            assertThatThrownBy(() -> paymentJpaRepository.save(payment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 오류");
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long id = 1L;
            when(entityManager.find(Payment.class, id))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<Payment> result = paymentJpaRepository.findById(id);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: 상태 업데이트 중 예외 발생")
        void updateStatus_Exception() {
            // given
            Long paymentId = 1L;
            PaymentStatus newStatus = PaymentStatus.PAID;

            when(entityManager.find(Payment.class, paymentId))
                    .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

            // when & then
            assertThatThrownBy(() -> paymentJpaRepository.updateStatus(paymentId, newStatus))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("데이터베이스 연결 오류");
        }
    }
}