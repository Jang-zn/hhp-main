package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryPaymentRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryPaymentRepository 단위 테스트")
class InMemoryPaymentRepositoryTest {

    private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
    }

    @Test
    @DisplayName("결제 저장 성공")
    void save_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("120000"))
                .build();
        
        Payment payment = Payment.builder()
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

    @Test
    @DisplayName("결제 ID로 조회 성공")
    void findById_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .build();
        
        Payment payment = Payment.builder()
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
    @DisplayName("존재하지 않는 결제 조회")
    void findById_NotFound() {
        // when
        Optional<Payment> foundPayment = paymentRepository.findById(999L);

        // then
        assertThat(foundPayment).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("providePaymentData")
    @DisplayName("다양한 결제 데이터로 저장")
    void save_WithDifferentPaymentData(String userName, String amount, PaymentStatus status) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal(amount))
                .build();
        
        Payment payment = Payment.builder()
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

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of("홍길동", "100000", PaymentStatus.PENDING),
                Arguments.of("김철수", "250000", PaymentStatus.PAID),
                Arguments.of("이영희", "75000", PaymentStatus.FAILED)
        );
    }
}