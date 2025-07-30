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
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private CreatePaymentUseCase createPaymentUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createPaymentUseCase = new CreatePaymentUseCase(paymentRepositoryPort, orderRepositoryPort, userRepositoryPort);
        
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
        Long orderId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("50000");
        Long couponId = null;
        
        Payment expectedPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .user(testUser)
            .amount(amount)
            .status(PaymentStatus.COMPLETED)
            .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);
        
        // when
        Payment result = createPaymentUseCase.execute(orderId, userId, amount, couponId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(testOrder);
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        
        verify(paymentRepositoryPort).save(any(Payment.class));
    }
    
    @Test
    @DisplayName("성공 - 결제 생성 (쿠폰 포함)")
    void execute_WithCoupon_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("45000");
        Long couponId = 1L;
        
        Payment expectedPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .user(testUser)
            .amount(amount)
            .couponId(couponId)
            .status(PaymentStatus.COMPLETED)
            .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);
        
        // when
        Payment result = createPaymentUseCase.execute(orderId, userId, amount, couponId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(testOrder);
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        
        verify(paymentRepositoryPort).save(any(Payment.class));
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 주문")
    void execute_OrderNotFound() {
        // given
        Long orderId = 999L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("50000");
        Long couponId = null;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> createPaymentUseCase.execute(orderId, userId, amount, couponId))
            .isInstanceOf(OrderException.NotFound.class);
            
        verify(paymentRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 사용자")
    void execute_UserNotFound() {
        // given
        Long orderId = 1L;
        Long userId = 999L;
        BigDecimal amount = new BigDecimal("50000");
        Long couponId = null;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> createPaymentUseCase.execute(orderId, userId, amount, couponId))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(paymentRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 잘못된 결제 금액 (음수)")
    void execute_InvalidAmount() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("-10000");
        Long couponId = null;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        
        // when & then
        assertThatThrownBy(() -> createPaymentUseCase.execute(orderId, userId, amount, couponId))
            .isInstanceOf(PaymentException.InvalidAmount.class);
            
        verify(paymentRepositoryPort, never()).save(any());
    }
}