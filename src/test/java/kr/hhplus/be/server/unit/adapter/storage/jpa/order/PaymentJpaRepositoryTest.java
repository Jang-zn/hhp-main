package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.adapter.storage.jpa.PaymentJpaRepository;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("결제 데이터 저장소 비즈니스 시나리오")
class PaymentJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestEntityManager testEntityManager;
    
    private PaymentJpaRepository paymentJpaRepository;

    @BeforeEach
    void setUp() {
        paymentJpaRepository = new PaymentJpaRepository(testEntityManager.getEntityManager());
    }

    @Test
    @DisplayName("새로운 결제 정보를 저장할 수 있다")
    void canSaveNewPayment() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .status(PaymentStatus.PENDING)
                .build();

        // When
        Payment savedPayment = paymentJpaRepository.save(payment);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Payment foundPayment = testEntityManager.find(Payment.class, savedPayment.getId());
        assertThat(foundPayment).isNotNull();
        assertThat(foundPayment.getOrderId()).isEqualTo(1L);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("주문 ID로 결제 정보를 조회할 수 있다")
    void canFindPaymentByOrderId() {
        // Given
        Long orderId = 1L;
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(orderId)
                .status(PaymentStatus.PENDING)
                .build();
        testEntityManager.persistAndFlush(payment);
        testEntityManager.clear();

        // When
        List<Payment> foundPayments = paymentJpaRepository.findByOrderId(orderId);

        // Then
        assertThat(foundPayments).hasSize(1);
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
        Payment savedPayment = paymentJpaRepository.save(payment);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Payment foundPayment = testEntityManager.find(Payment.class, savedPayment.getId());
        assertThat(foundPayment.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("기본 조회 기능만 테스트")
    void canPerformBasicQueries() {
        // Given
        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(1L)
                .status(PaymentStatus.PENDING)
                .build();
        testEntityManager.persistAndFlush(payment);
        testEntityManager.clear();

        // When & Then - 기본 조회 기능 확인
        List<Payment> payments = paymentJpaRepository.findByOrderId(1L);
        assertThat(payments).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenPaymentNotFoundByOrderId() {
        // Given
        Long nonExistentOrderId = 999L;

        // When
        List<Payment> foundPayments = paymentJpaRepository.findByOrderId(nonExistentOrderId);

        // Then
        assertThat(foundPayments).isEmpty();
    }

    @Test
    @DisplayName("null 결제 정보 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullPayment() {
        // When & Then
        assertThatThrownBy(() -> paymentJpaRepository.save(null))
                .isInstanceOf(Exception.class);
    }
}