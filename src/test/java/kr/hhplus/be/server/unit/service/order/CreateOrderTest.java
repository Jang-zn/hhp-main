package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import kr.hhplus.be.server.domain.usecase.order.*;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.service.KeyGenerator;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService.createOrder 메서드 테스트
 */
@DisplayName("주문 생성 서비스")
class CreateOrderTest {

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
    @Mock private CachePort cachePort;
    @Mock private KeyGenerator keyGenerator;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(transactionTemplate, createOrderUseCase, getOrderUseCase, getOrderListUseCase, validateOrderUseCase, completeOrderUseCase, createPaymentUseCase, deductBalanceUseCase, applyCouponUseCase, lockingPort, userRepositoryPort, cachePort, keyGenerator);
    }

    @Test
    @DisplayName("정상적인 주문 생성이 성공한다")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        List<ProductQuantityDto> productQuantities = List.of(
            new ProductQuantityDto(1L, 2),
            new ProductQuantityDto(2L, 1)
        );
        Order expectedOrder = TestBuilder.OrderBuilder.defaultOrder()
                .userId(userId)
                .build();
        
        when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
        when(createOrderUseCase.execute(userId, productQuantities)).thenReturn(expectedOrder);
        
        // when
        Order result = orderService.createOrder(userId, productQuantities);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        
        verify(lockingPort).acquireLock("order-creation-" + userId);
        verify(createOrderUseCase).execute(userId, productQuantities);
        verify(lockingPort).releaseLock("order-creation-" + userId);
    }
    
    @Test
    @DisplayName("락 획득 실패 시 동시성 충돌 예외가 발생한다")
    void createOrder_LockAcquisitionFailed() {
        // given
        Long userId = 1L;
        List<ProductQuantityDto> productQuantities = List.of(
            new ProductQuantityDto(1L, 2)
        );
        
        when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, productQuantities))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);
            
        verify(lockingPort).acquireLock("order-creation-" + userId);
        verify(createOrderUseCase, never()).execute(any(), any());
        verify(lockingPort, never()).releaseLock(any());
    }
    
    @Test
    @DisplayName("UseCase 실행 중 예외 발생 시 락이 해제된다")
    void createOrder_UseCaseException_ReleaseLock() {
        // given
        Long userId = 1L;
        List<ProductQuantityDto> productQuantities = List.of(
            new ProductQuantityDto(1L, 2)
        );
        
        when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
        when(createOrderUseCase.execute(userId, productQuantities))
            .thenThrow(new RuntimeException("Order creation failed"));
        
        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, productQuantities))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Order creation failed");
            
        verify(lockingPort).acquireLock("order-creation-" + userId);
        verify(createOrderUseCase).execute(userId, productQuantities);
        verify(lockingPort).releaseLock("order-creation-" + userId);
    }
}