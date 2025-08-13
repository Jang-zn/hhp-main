package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.usecase.order.*;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.service.KeyGenerator;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OrderService.getOrderWithDetails 메서드 테스트
 */
@DisplayName("주문 상세 조회 서비스")
class GetOrderWithDetailsTest {

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
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator lockKeyGenerator;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
            transactionTemplate, createOrderUseCase, getOrderUseCase, getOrderListUseCase, 
            validateOrderUseCase, completeOrderUseCase, createPaymentUseCase, deductBalanceUseCase, 
            applyCouponUseCase, lockingPort, userRepositoryPort, orderRepositoryPort, cachePort, lockKeyGenerator
        );
    }

    @Test
    @DisplayName("정상적인 주문 상세 조회가 성공한다")
    void getOrderWithDetails_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Order expectedOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .build();
        
        String cacheKey = "order:info:order_1";
        when(lockKeyGenerator.generateOrderCacheKey(orderId)).thenReturn(cacheKey);
        when(cachePort.get(eq(cacheKey), eq(Order.class), any())).thenAnswer(invocation -> {
            // 캐시 miss 시 supplier를 호출
            java.util.function.Supplier<Order> supplier = invocation.getArgument(2);
            return supplier.get();
        });
        when(orderRepositoryPort.findById(orderId)).thenReturn(java.util.Optional.of(expectedOrder));
        
        // when
        Order result = orderService.getOrderWithDetails(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        
        verify(lockKeyGenerator).generateOrderCacheKey(orderId);
        verify(cachePort).get(eq(cacheKey), eq(Order.class), any());
        verify(orderRepositoryPort).findById(orderId);
    }
}