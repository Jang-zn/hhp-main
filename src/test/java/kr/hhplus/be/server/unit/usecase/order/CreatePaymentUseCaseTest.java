package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.CreatePaymentUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreatePaymentUseCase 단위 테스트")
class CreatePaymentUseCaseTest {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;
    
    private CreatePaymentUseCase createPaymentUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createPaymentUseCase = new CreatePaymentUseCase(paymentRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50000"))
            .build();
    }

    @Test
    @DisplayName("성공 - 결제 생성 (쿠폰 없음)")
    void execute_WithoutCoupon_Success() {
        // given
        BigDecimal amount = new BigDecimal("50000");
        
        Payment expectedPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .user(testUser)
            .amount(amount)
            .status(PaymentStatus.PAID)
            .build();
        
        when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);
        
        // when
        Payment result = createPaymentUseCase.execute(testOrder, testUser, amount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(testOrder);
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        
        verify(paymentRepositoryPort).save(any(Payment.class));
    }
    
    @Test
    @DisplayName("성공 - 결제 생성 (쿠폰 포함)")
    void execute_WithCoupon_Success() {
        // given
        BigDecimal amount = new BigDecimal("45000");
        
        Payment expectedPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .user(testUser)
            .amount(amount)
            .status(PaymentStatus.PAID)
            .build();
        
        when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);
        
        // when
        Payment result = createPaymentUseCase.execute(testOrder, testUser, amount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(testOrder);
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        
        verify(paymentRepositoryPort).save(any(Payment.class));
    }
    
    @Test
    @DisplayName("실패 - 잘못된 결제 금액 (음수)")
    void execute_InvalidAmount() {
        // given
        BigDecimal amount = new BigDecimal("-10000");
        
        // when & then
        assertThatThrownBy(() -> createPaymentUseCase.execute(testOrder, testUser, amount))
            .isInstanceOf(IllegalArgumentException.class);
            
        verify(paymentRepositoryPort, never()).save(any());
    }
}