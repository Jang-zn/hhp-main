package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.BalanceException;
import kr.hhplus.be.server.domain.exception.CouponException;
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
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
        
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
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
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
            Coupon coupon = Coupon.builder().id(couponId).discountRate(new BigDecimal("0.1")).build();
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
                .hasMessage("Order not found");
        
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
                .hasMessage("Unauthorized access to order");
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
                .hasMessage("Coupon not found");
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
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
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
                .hasMessage("Insufficient balance");
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
                .isInstanceOf(OrderException.ConcurrencyConflict.class)
                .hasMessage("Concurrent order creation conflict");
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
                .hasMessage("Unauthorized access to order");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidIds")
    @DisplayName("비정상 ID 값들로 결제 테스트")
    void payOrder_WithInvalidIds(Long orderId, Long userId, Class<? extends Exception> expectedException) {
        // given
        if (orderId != null && orderId <= 0) {
            // orderId가 0 이하인 경우는 validateParameters에서 IllegalArgumentException 발생
            assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                    .isInstanceOf(expectedException);
            return;
        }
        
        if (userId != null && userId <= 0) {
            // userId가 0 이하인 경우는 validateParameters에서 IllegalArgumentException 발생
            assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                    .isInstanceOf(expectedException);
            return;
        }

        User user = User.builder().id(userId != null ? userId : 1L).name("테스트 사용자").build();
        Order order = Order.builder().id(orderId != null ? orderId : 1L).user(user).totalAmount(new BigDecimal("100000")).items(Collections.emptyList()).build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        
        // 예외가 UserException.NotFound인 경우 user를 찾지 못하도록 설정
        if (expectedException == UserException.NotFound.class) {
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        } else {
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        }
        
        // 예외가 OrderException.NotFound인 경우 order를 찾지 못하도록 설정
        if (expectedException == OrderException.NotFound.class) {
            when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());
        } else {
            when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        }

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(expectedException);
    }

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of(1L, 1L, 1L), // 쿠폰 사용
                Arguments.of(2L, 2L, null), // 쿠폰 미사용
                Arguments.of(3L, 3L, 2L) // 다른 쿠폰 사용
        );
    }

    @Test
    @DisplayName("동시 결제 요청 시 락 충돌 확인")
    void payOrder_ConcurrencyConflict_LockContention() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        // 첫 번째 payment 락은 성공하지만 balance 락 실패 시나리오
        when(lockingPort.acquireLock("payment-1")).thenReturn(true);
        when(lockingPort.acquireLock("balance-1")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(OrderException.ConcurrencyConflict.class)
                .hasMessage("Concurrent order creation conflict");
        
        // payment 락이 해제되어야 함
        verify(lockingPort).releaseLock("payment-1");
        verify(lockingPort, never()).releaseLock("balance-1"); // balance 락은 획득하지 못했으므로 해제할 필요 없음
    }

    @Test
    @DisplayName("재고 확정 실패 시 잔액 복구 및 예외 발생")
    void payOrder_StockConfirmationFailure_ShouldRollbackBalance() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        BigDecimal originalBalance = new BigDecimal("200000");
        BigDecimal orderAmount = new BigDecimal("100000");
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Product product = Product.builder().id(1L).name("상품1").price(orderAmount).stock(0).reservedStock(1).build(); // 재고 없음
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
        Order order = Order.builder().id(orderId).user(user).totalAmount(orderAmount).items(List.of(orderItem)).build();
        Balance balance = Balance.builder().user(user).amount(originalBalance).build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(RuntimeException.class); // 재고 확정 시 발생할 예외
        
        // 잔액이 원래대로 복구되어야 함 (트랜잭션 롤백에 의해)
        verify(lockingPort, times(2)).releaseLock(anyString()); // 락이 해제되어야 함
    }

    @Test
    @DisplayName("이미 결제된 주문 재결제 시 예외 발생")
    void payOrder_AlreadyPaidOrder() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        User user = User.builder().id(userId).name("테스트 사용자").build();
        Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(Collections.emptyList()).build();
        Payment existingPayment = Payment.builder().order(order).user(user).amount(new BigDecimal("100000")).status(PaymentStatus.PAID).build();

        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(List.of(existingPayment));

        // when & then
        assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                .isInstanceOf(OrderException.AlreadyPaid.class)
                .hasMessage("Order is already paid");
        
        verify(lockingPort, times(2)).releaseLock(anyString()); // 락이 해제되어야 함
    }

    @Nested
    @DisplayName("트랜잭션 테스트")
    class TransactionTest {
        
        @Test
        @DisplayName("결제 중 예외 발생 시 데이터 일관성 유지")
        void payOrder_ExceptionDuringPayment() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Product product = Product.builder().id(1L).name("상품1").price(new BigDecimal("100000")).stock(10).reservedStock(1).build();
            OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
            Order order = Order.builder().id(orderId).user(user).totalAmount(new BigDecimal("100000")).items(List.of(orderItem)).build();
            Balance balance = Balance.builder().user(user).amount(new BigDecimal("200000")).build();

            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
            when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(productRepositoryPort.save(any(Product.class))).thenThrow(new RuntimeException("DB 저장 실패"));

            // when & then
            assertThatThrownBy(() -> payOrderUseCase.execute(orderId, userId, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("재고 확정 실패");
            
            // 락이 해제되어야 함
            verify(lockingPort, times(2)).releaseLock(anyString());
        }
    }

    private static Stream<Arguments> provideInvalidIds() {
        return Stream.of(
                Arguments.of(-1L, 1L, IllegalArgumentException.class), // orderId가 -1이면 IllegalArgumentException
                Arguments.of(1L, -1L, IllegalArgumentException.class), // userId가 -1이면 IllegalArgumentException
                Arguments.of(0L, 1L, IllegalArgumentException.class), // orderId가 0이면 IllegalArgumentException
                Arguments.of(1L, 0L, IllegalArgumentException.class), // userId가 0이면 IllegalArgumentException
                Arguments.of(Long.MAX_VALUE, 1L, OrderException.NotFound.class), // orderId가 MAX_VALUE면 OrderException.NotFound
                Arguments.of(1L, Long.MAX_VALUE, UserException.NotFound.class) // userId가 MAX_VALUE면 UserException.NotFound
        );
    }
} 