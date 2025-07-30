package kr.hhplus.be.server.unit.facade.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.facade.order.PayOrderFacade;
import kr.hhplus.be.server.domain.usecase.order.ValidateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CreatePaymentUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PayOrderFacade 단위 테스트")
class PayOrderFacadeTest {

    @Mock
    private ValidateOrderUseCase validateOrderUseCase;
    
    @Mock
    private DeductBalanceUseCase deductBalanceUseCase;
    
    @Mock
    private ApplyCouponUseCase applyCouponUseCase;
    
    @Mock
    private CompleteOrderUseCase completeOrderUseCase;
    
    @Mock
    private CreatePaymentUseCase createPaymentUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    private PayOrderFacade payOrderFacade;
    
    private User testUser;
    private Order testOrder;
    private Payment testPayment;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payOrderFacade = new PayOrderFacade(
            validateOrderUseCase,
            deductBalanceUseCase,
            applyCouponUseCase,
            completeOrderUseCase,
            createPaymentUseCase,
            lockingPort
        );
        
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
            
        testPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .user(testUser)
            .amount(new BigDecimal("50000"))
            .status(PaymentStatus.COMPLETED)
            .build();
    }

    @Nested
    @DisplayName("결제 처리")
    class PayOrder {
        
        @Test
        @DisplayName("성공 - 쿠폰 없는 일반 결제")
        void payOrder_WithoutCoupon_Success() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            Long couponId = null;
            
            when(lockingPort.acquireLock("payment-" + orderId)).thenReturn(true);
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(validateOrderUseCase.execute(orderId, userId)).thenReturn(testOrder);
            when(deductBalanceUseCase.execute(userId, testOrder.getTotalAmount())).thenReturn(new BigDecimal("950000"));
            when(completeOrderUseCase.execute(orderId)).thenReturn(testOrder);
            when(createPaymentUseCase.execute(orderId, userId, testOrder.getTotalAmount(), null)).thenReturn(testPayment);
            
            // when
            Payment result = payOrderFacade.payOrder(orderId, userId, couponId);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            
            // UseCase 호출 순서 검증
            verify(validateOrderUseCase).execute(orderId, userId);
            verify(deductBalanceUseCase).execute(userId, testOrder.getTotalAmount());
            verify(applyCouponUseCase, never()).execute(any(), any());
            verify(completeOrderUseCase).execute(orderId);
            verify(createPaymentUseCase).execute(orderId, userId, testOrder.getTotalAmount(), null);
            
            // Lock 해제 검증
            verify(lockingPort).releaseLock("payment-" + orderId);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
        
        @Test
        @DisplayName("성공 - 쿠폰 적용 결제")
        void payOrder_WithCoupon_Success() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            Long couponId = 1L;
            BigDecimal discountedAmount = new BigDecimal("40000");
            
            when(lockingPort.acquireLock("payment-" + orderId)).thenReturn(true);
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(validateOrderUseCase.execute(orderId, userId)).thenReturn(testOrder);
            when(applyCouponUseCase.execute(couponId, testOrder.getTotalAmount())).thenReturn(discountedAmount);
            when(deductBalanceUseCase.execute(userId, discountedAmount)).thenReturn(new BigDecimal("960000"));
            when(completeOrderUseCase.execute(orderId)).thenReturn(testOrder);
            when(createPaymentUseCase.execute(orderId, userId, discountedAmount, couponId)).thenReturn(testPayment);
            
            // when
            Payment result = payOrderFacade.payOrder(orderId, userId, couponId);
            
            // then
            assertThat(result).isNotNull();
            
            // UseCase 호출 순서 검증
            verify(validateOrderUseCase).execute(orderId, userId);
            verify(applyCouponUseCase).execute(couponId, testOrder.getTotalAmount());
            verify(deductBalanceUseCase).execute(userId, discountedAmount);
            verify(completeOrderUseCase).execute(orderId);
            verify(createPaymentUseCase).execute(orderId, userId, discountedAmount, couponId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void payOrder_LockAcquisitionFailed() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            Long couponId = null;
            
            when(lockingPort.acquireLock("payment-" + orderId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> payOrderFacade.payOrder(orderId, userId, couponId))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            // UseCase 호출되지 않음 검증
            verify(validateOrderUseCase, never()).execute(any(), any());
            verify(deductBalanceUseCase, never()).execute(any(), any());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생 시 락 해제")
        void payOrder_UseCaseException_ReleaseLock() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            Long couponId = null;
            
            when(lockingPort.acquireLock("payment-" + orderId)).thenReturn(true);
            when(lockingPort.acquireLock("balance-" + userId)).thenReturn(true);
            when(validateOrderUseCase.execute(orderId, userId)).thenThrow(new OrderException.OrderNotFound());
            
            // when & then
            assertThatThrownBy(() -> payOrderFacade.payOrder(orderId, userId, couponId))
                .isInstanceOf(OrderException.OrderNotFound.class);
                
            // Lock 해제 검증
            verify(lockingPort).releaseLock("payment-" + orderId);
            verify(lockingPort).releaseLock("balance-" + userId);
        }
    }
}