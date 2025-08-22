package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.Order;
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
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OrderService.getOrder 메서드 테스트
 */
@DisplayName("주문 조회 서비스")
class GetOrderTest {

    @Mock private TransactionTemplate transactionTemplate;
    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderUseCase getOrderUseCase;
    @Mock private GetOrderListUseCase getOrderListUseCase;
    @Mock private ValidateOrderUseCase validateOrderUseCase;
    @Mock private CompleteOrderUseCase completeOrderUseCase;
    @Mock private CreatePaymentUseCase createPaymentUseCase;
    @Mock private DeductBalanceUseCase deductBalanceUseCase;
    @Mock private ApplyCouponUseCase applyCouponUseCase;
    @Mock private LockingPort lockingPort;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private OrderItemRepositoryPort orderItemRepositoryPort;
    @Mock private CachePort cachePort;
    @Mock private KeyGenerator keyGenerator;
    @Mock private ApplicationEventPublisher eventPublisher;
    
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
    @DisplayName("정상적인 주문 조회가 성공한다")
    void getOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Order expectedOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .build();
        
        String cacheKey = "order:info:order_1";
        when(keyGenerator.generateOrderCacheKey(orderId)).thenReturn(cacheKey);
        when(cachePort.get(eq(cacheKey), eq(Order.class))).thenReturn(null); // Cache miss
        when(getOrderUseCase.execute(orderId, userId)).thenReturn(java.util.Optional.of(expectedOrder));
        
        // when
        Order result = orderService.getOrder(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        
        verify(getOrderUseCase).execute(orderId, userId);
    }
}