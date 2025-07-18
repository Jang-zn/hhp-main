package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.PaymentException;
import kr.hhplus.be.server.domain.exception.BalanceException;

@DisplayName("PayOrderUseCase 단위 테스트")
class PayOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private PayOrderUseCase payOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payOrderUseCase = new PayOrderUseCase(
                null, null, orderRepositoryPort, paymentRepositoryPort,
                null, eventLogRepositoryPort, lockingPort, cachePort, null
        );
    }

    @Test
    @DisplayName("주문 결제 성공")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = 1L;
        
        Order order = Order.builder()
                .totalAmount(new BigDecimal("1200000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
    }

    @ParameterizedTest
    @MethodSource("providePaymentData")
    @DisplayName("다양한 결제 시나리오")
    void payOrder_WithDifferentScenarios(Long orderId, Long userId, Long couponId) {
        // given
        Order order = Order.builder()
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 예외 발생")
    void payOrder_OrderNotFound() {
        // given
        Long orderId = 999L;
        Long userId = 1L;
        Long couponId = null;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(PaymentException.OrderNotFound.class)
                .hasMessage("Order not found");
    }

    @Test
    @DisplayName("다른 사용자의 주문 결제 시 예외 발생")
    void payOrder_UnauthorizedAccess() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long actualUserId = 2L; // 다른 사용자
        Long couponId = null;
        
        User actualUser = User.builder()
                .name("다른 사용자")
                .build();
        
        Order order = Order.builder()
                .user(actualUser) // 다른 사용자의 주문
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    @DisplayName("잘못된 쿠폰 ID로 결제 시 예외 발생")
    void payOrder_InvalidCoupon() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = 999L; // 존재하지 않는 쿠폰
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(PaymentException.InvalidCoupon.class)
                .hasMessage("Invalid coupon ID");
    }

    @Test
    @DisplayName("잔액 부족으로 결제 시 예외 발생")
    void payOrder_InsufficientBalance() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenThrow(new PaymentException.InsufficientBalance());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(PaymentException.InsufficientBalance.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    @DisplayName("동시성 충돌로 결제 시 예외 발생")
    void payOrder_ConcurrencyConflict() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenThrow(new PaymentException.ConcurrencyConflict());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(PaymentException.ConcurrencyConflict.class)
                .hasMessage("Concurrent payment conflict");
    }

    @Test
    @DisplayName("null 주문 ID로 결제 시 예외 발생")
    void payOrder_WithNullOrderId() {
        // given
        Long orderId = null;
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 사용자 ID로 결제 시 예외 발생")
    void payOrder_WithNullUserId() {
        // given
        Long orderId = 1L;
        Long userId = null;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비정상적인 주문 상태로 결제 시 예외 발생")
    void payOrder_InvalidOrderStatus() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenThrow(new PaymentException.InvalidOrderStatus());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(PaymentException.InvalidOrderStatus.class)
                .hasMessage("Order status not eligible for payment");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidIds")
    @DisplayName("비정상 ID 값들로 결제 테스트")
    void payOrder_WithInvalidIds(Long orderId, Long userId) {
        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of(1L, 1L, 1L), // 쿠폰 사용
                Arguments.of(2L, 2L, null), // 쿠폰 미사용
                Arguments.of(3L, 3L, 2L) // 다른 쿠폰 사용
        );
    }

    private static Stream<Arguments> provideInvalidIds() {
        return Stream.of(
                Arguments.of(-1L, 1L),
                Arguments.of(1L, -1L),
                Arguments.of(0L, 1L),
                Arguments.of(1L, 0L),
                Arguments.of(Long.MAX_VALUE, 1L),
                Arguments.of(1L, Long.MAX_VALUE)
        );
    }
} 