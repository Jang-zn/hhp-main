package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PayOrderUseCase 단위 테스트")
class PayOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private MessagingPort messagingPort;

    private PayOrderUseCase payOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payOrderUseCase = new PayOrderUseCase(
                userRepositoryPort, balanceRepositoryPort, orderRepositoryPort, paymentRepositoryPort,
                couponRepositoryPort, productRepositoryPort, eventLogRepositoryPort, 
                lockingPort, cachePort, messagingPort
        );
    }

    @Test
    @DisplayName("주문 결제 성공")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Product product = Product.builder().id(1L).name("상품1").price(new BigDecimal("100000")).stock(10).reservedStock(1).build();
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).price(product.getPrice()).build();
        
        Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(List.of(orderItem)).build();
        
        Balance balance = Balance.builder().user(user).amount(new BigDecimal("200000")).build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            return Payment.builder()
                    .id(1L)
                    .order(payment.getOrder())
                    .user(payment.getUser())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .build();
        });
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(productRepositoryPort, times(1)).save(any(Product.class));
    }

    @ParameterizedTest
    @MethodSource("providePaymentData")
    @DisplayName("다양한 결제 시나리오")
    void payOrder_WithDifferentScenarios(Long orderId, Long userId, Long couponId) {
        // given
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Product product = Product.builder().id(1L).name("상품1").price(new BigDecimal("100000")).stock(10).reservedStock(1).build();
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).price(product.getPrice()).build();
        Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(List.of(orderItem)).build();
        Balance balance = Balance.builder().user(user).amount(new BigDecimal("200000")).build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            return Payment.builder()
                    .id(1L)
                    .order(payment.getOrder())
                    .user(payment.getUser())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .build();
        });
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        if (couponId != null) {
            Coupon coupon = Coupon.builder().id(couponId).discountRate(new BigDecimal("0.1")).status(CouponStatus.ACTIVE).build();
            when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        }

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
        verify(productRepositoryPort, times(order.getItems().size())).save(any(Product.class));
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 예외 발생")
    void payOrder_OrderNotFound() {
        // given
        Long orderId = 999L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(OrderException.NotFound.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());
        
        verify(lockingPort, times(2)).releaseLock(anyString());
    }

    @Test
    @DisplayName("다른 사용자의 주문 결제 시 예외 발생")
    void payOrder_UnauthorizedAccess() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long actualUserId = 2L; // 다른 사용자
        Long couponId = null;
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        User actualUser = User.builder().id(actualUserId).name("다른 사용자").build();
        
        Order order = Order.builder()
                .user(actualUser) // 다른 사용자의 주문
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(OrderException.Unauthorized.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
    }

    @Test
    @DisplayName("잘못된 쿠폰 ID로 결제 시 예외 발생")
    void payOrder_InvalidCoupon() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = 999L; // 존재하지 않는 쿠폰
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(Collections.emptyList()).build();
        Balance balance = Balance.builder().user(user).amount(new BigDecimal("200000")).build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(ErrorCode.COUPON_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("잔액 부족으로 결제 시 예외 발생")
    void payOrder_InsufficientBalance() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Product product = Product.builder().id(1L).name("상품1").price(new BigDecimal("100000")).stock(10).reservedStock(1).build();
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).price(product.getPrice()).build();
        Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(List.of(orderItem)).build();
        Balance balance = Balance.builder().user(user).amount(new BigDecimal("50000")).build(); // 잔액 부족
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(BalanceException.InsufficientBalance.class)
                .hasMessage(ErrorCode.INSUFFICIENT_BALANCE.getMessage());
    }

    @Test
    @DisplayName("동시성 충돌로 결제 시 예외 발생")
    void payOrder_ConcurrencyConflict() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        when(lockingPort.acquireLock(anyString())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(CommonException.ConcurrencyConflict.class)
                .hasMessage(ErrorCode.CONCURRENCY_ERROR.getMessage());
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
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        User anotherUser = User.builder().id(99L).name("다른 사용자").build(); // 다른 사용자
        
        Order order = Order.builder()
                .user(anotherUser) // 다른 사용자의 주문
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, couponId))
                .isInstanceOf(OrderException.Unauthorized.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
    }

    @Test
    @DisplayName("음수 주문 ID로 결제 시 예외 발생")
    void payOrder_WithNegativeOrderId_ShouldThrowException() {
        // given
        Long orderId = -1L;
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 사용자 ID로 결제 시 예외 발생")
    void payOrder_WithNegativeUserId_ShouldThrowException() {
        // given
        Long orderId = 1L;
        Long userId = -1L;

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 결제 시 예외 발생")
    void payOrder_WithNonExistentOrderId_ShouldThrowException() {
        // given
        Long orderId = Long.MAX_VALUE;
        Long userId = 1L;
        User user = User.builder().id(userId).name("테스트 사용자").build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());


        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(OrderException.NotFound.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 결제 시 예외 발생")
    void payOrder_WithNonExistentUserId_ShouldThrowException() {
        // given
        Long orderId = 1L;
        Long userId = Long.MAX_VALUE;

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(UserException.NotFound.class);
    }

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of(1L, 1L, 1L), // 쿠폰 사용
                Arguments.of(2L, 2L, null), // 쿠폰 미사용
                Arguments.of(3L, 3L, 2L) // 다른 쿠폰 사용
        );
    }
} 