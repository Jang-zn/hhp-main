package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.usecase.order.*;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.springframework.context.ApplicationEventPublisher;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService.payOrder 메서드 테스트
 */
@DisplayName("주문 결제 서비스")
class PayOrderTest {

    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private CreateOrderUseCase createOrderUseCase;
    
    @Mock
    private GetOrderUseCase getOrderUseCase;
    
    @Mock
    private GetOrderListUseCase getOrderListUseCase;
    
    @Mock
    private ValidateOrderUseCase validateOrderUseCase;
    
    @Mock
    private CompleteOrderUseCase completeOrderUseCase;
    
    @Mock
    private CreatePaymentUseCase createPaymentUseCase;
    
    @Mock
    private DeductBalanceUseCase deductBalanceUseCase;
    
    @Mock
    private ApplyCouponUseCase applyCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private OrderItemRepositoryPort orderItemRepositoryPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
            transactionTemplate, createOrderUseCase, getOrderUseCase, getOrderListUseCase, 
            validateOrderUseCase, completeOrderUseCase, createPaymentUseCase, deductBalanceUseCase, 
            applyCouponUseCase, lockingPort, userRepositoryPort, orderRepositoryPort, 
            orderItemRepositoryPort, keyGenerator, eventPublisher
        );
    }

    @Test
    @DisplayName("정상적인 주문 결제가 성공한다")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = 1L;
        BigDecimal orderAmount = new BigDecimal("50000");
        BigDecimal finalAmount = new BigDecimal("45000");
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .totalAmount(orderAmount)
                .build();
        Payment expectedPayment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(orderId)
                .userId(userId)
                .amount(finalAmount)
                .build();
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_1";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(true);
        when(lockingPort.acquireLock(balanceLockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(validateOrderUseCase.execute(orderId, userId)).thenReturn(order);
        when(applyCouponUseCase.execute(orderAmount, couponId)).thenReturn(finalAmount);
        when(createPaymentUseCase.execute(orderId, userId, finalAmount)).thenReturn(expectedPayment);
        
        // when
        Payment result = orderService.payOrder(orderId, userId, couponId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAmount()).isEqualTo(finalAmount);
        
        verify(keyGenerator).generateOrderPaymentKey(orderId);
        verify(keyGenerator).generateBalanceKey(userId);
        verify(lockingPort).acquireLock(paymentLockKey);
        verify(lockingPort).acquireLock(balanceLockKey);
        verify(transactionTemplate).execute(any());
        verify(userRepositoryPort).existsById(userId);
        verify(validateOrderUseCase).execute(orderId, userId);
        verify(applyCouponUseCase).execute(orderAmount, couponId);
        verify(deductBalanceUseCase).execute(userId, finalAmount);
        verify(completeOrderUseCase).execute(order);
        verify(createPaymentUseCase).execute(orderId, userId, finalAmount);
        verify(lockingPort).releaseLock(balanceLockKey);
        verify(lockingPort).releaseLock(paymentLockKey);
    }
    
    @Test
    @DisplayName("쿠폰 없이 주문 결제가 성공한다")
    void payOrder_WithoutCoupon_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        BigDecimal orderAmount = new BigDecimal("50000");
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .totalAmount(orderAmount)
                .build();
        Payment expectedPayment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(orderId)
                .userId(userId)
                .amount(orderAmount)
                .build();
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_1";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(true);
        when(lockingPort.acquireLock(balanceLockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(validateOrderUseCase.execute(orderId, userId)).thenReturn(order);
        when(applyCouponUseCase.execute(orderAmount, couponId)).thenReturn(orderAmount);
        when(createPaymentUseCase.execute(orderId, userId, orderAmount)).thenReturn(expectedPayment);
        
        // when
        Payment result = orderService.payOrder(orderId, userId, couponId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(orderAmount);
        
        verify(applyCouponUseCase).execute(orderAmount, couponId);
    }
    
    @Test
    @DisplayName("결제 락 획득 실패 시 동시성 충돌 예외가 발생한다")
    void payOrder_PaymentLockAcquisitionFailed() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_1";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(orderId, userId, couponId))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(keyGenerator).generateOrderPaymentKey(orderId);
        verify(lockingPort).acquireLock(paymentLockKey);
        verify(lockingPort, never()).acquireLock(balanceLockKey);
        verify(userRepositoryPort, never()).existsById(any());
    }
    
    @Test
    @DisplayName("잔액 락 획득 실패 시 결제 락을 해제하고 동시성 충돌 예외가 발생한다")
    void payOrder_BalanceLockAcquisitionFailed() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_1";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(true);
        when(lockingPort.acquireLock(balanceLockKey)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(orderId, userId, couponId))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(keyGenerator).generateOrderPaymentKey(orderId);
        verify(keyGenerator).generateBalanceKey(userId);
        verify(lockingPort).acquireLock(paymentLockKey);
        verify(lockingPort).acquireLock(balanceLockKey);
        verify(lockingPort).releaseLock(paymentLockKey);
        verify(userRepositoryPort, never()).existsById(any());
    }
    
    @Test
    @DisplayName("존재하지 않는 사용자로 결제 요청 시 예외가 발생한다")
    void payOrder_UserNotFound() {
        // given
        Long orderId = 1L;
        Long userId = 999L;
        Long couponId = null;
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_999";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(true);
        when(lockingPort.acquireLock(balanceLockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepositoryPort.existsById(userId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(orderId, userId, couponId))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(transactionTemplate).execute(any());
        verify(userRepositoryPort).existsById(userId);
        verify(lockingPort).releaseLock(balanceLockKey);
        verify(lockingPort).releaseLock(paymentLockKey);
    }
    
    @Test
    @DisplayName("결제 과정 중 예외 발생 시 락이 해제된다")
    void payOrder_ExceptionDuringPayment_ReleaseLocks() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;
        BigDecimal orderAmount = new BigDecimal("50000");
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .totalAmount(orderAmount)
                .build();
        
        String paymentLockKey = "order:payment:order_1";
        String balanceLockKey = "balance:user_1";
        when(keyGenerator.generateOrderPaymentKey(orderId)).thenReturn(paymentLockKey);
        when(keyGenerator.generateBalanceKey(userId)).thenReturn(balanceLockKey);
        when(lockingPort.acquireLock(paymentLockKey)).thenReturn(true);
        when(lockingPort.acquireLock(balanceLockKey)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(validateOrderUseCase.execute(orderId, userId)).thenReturn(order);
        when(applyCouponUseCase.execute(orderAmount, couponId)).thenReturn(orderAmount);
        when(deductBalanceUseCase.execute(userId, orderAmount))
            .thenThrow(new RuntimeException("Insufficient balance"));
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(orderId, userId, couponId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Insufficient balance");
            
        verify(transactionTemplate).execute(any());
        verify(lockingPort).releaseLock(balanceLockKey);
        verify(lockingPort).releaseLock(paymentLockKey);
    }
}