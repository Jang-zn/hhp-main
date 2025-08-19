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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OrderService.getOrderList 메서드 테스트
 */
@DisplayName("주문 목록 조회 서비스")
class GetOrderListTest {

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
            orderItemRepositoryPort, cachePort, keyGenerator, eventPublisher
        );
    }

    @Test
    @DisplayName("정상적인 주문 목록 조회가 성공한다")
    void getOrderList_Success() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        List<Order> expectedOrders = List.of(
            TestBuilder.OrderBuilder.defaultOrder().userId(userId).build(),
            TestBuilder.OrderBuilder.defaultOrder().userId(userId).build()
        );
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        String cacheKey = "order:list:user_1:limit_10:offset_0";
        when(keyGenerator.generateOrderListCacheKey(userId, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getOrderListUseCase.execute(userId, limit, offset)).thenReturn(expectedOrders);
        
        // when
        List<Order> result = orderService.getOrderList(userId, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(1).getUserId()).isEqualTo(userId);
        
        verify(userRepositoryPort).existsById(userId);
        verify(keyGenerator).generateOrderListCacheKey(userId, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(expectedOrders), anyInt());
        verify(getOrderListUseCase).execute(userId, limit, offset);
    }
    
    @Test
    @DisplayName("빈 주문 목록 조회가 성공한다")
    void getOrderList_EmptyList() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        String cacheKey = "order:list:user_1:limit_10:offset_0";
        when(keyGenerator.generateOrderListCacheKey(userId, limit, offset)).thenReturn(cacheKey);
        when(cachePort.getList(eq(cacheKey))).thenReturn(null); // Cache miss
        when(getOrderListUseCase.execute(userId, limit, offset)).thenReturn(List.of());
        
        // when
        List<Order> result = orderService.getOrderList(userId, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(userRepositoryPort).existsById(userId);
        verify(keyGenerator).generateOrderListCacheKey(userId, limit, offset);
        verify(cachePort).getList(eq(cacheKey));
        verify(cachePort).put(eq(cacheKey), eq(List.of()), anyInt());
        verify(getOrderListUseCase).execute(userId, limit, offset);
    }
}