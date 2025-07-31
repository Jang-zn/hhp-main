package kr.hhplus.be.server.unit.facade.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.order.CreateOrderFacade;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreateOrderFacade 단위 테스트")
class CreateOrderFacadeTest {

    @Mock
    private CreateOrderUseCase createOrderUseCase;
    @Mock
    private LockingPort lockingPort;
    
    private CreateOrderFacade createOrderFacade;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderFacade = new CreateOrderFacade(createOrderUseCase, lockingPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        Product product = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("50000"))
            .build();
            
        OrderItem orderItem = OrderItem.builder()
            .product(product)
            .quantity(2)
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("100000"))
            .items(List.of(orderItem))
            .build();
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {
        
        @Test
        @DisplayName("성공 - 정상 주문 생성")
        void createOrder_Success() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2, 2L, 1);
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(createOrderUseCase.execute(userId, productQuantities)).thenReturn(testOrder);
            
            // when
            Order result = createOrderFacade.createOrder(userId, productQuantities);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("100000"));
            
            verify(lockingPort).acquireLock(anyString());
            verify(lockingPort).releaseLock(anyString());
            verify(createOrderUseCase).execute(userId, productQuantities);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패로 인한 동시성 충돌")
        void createOrder_ConcurrencyConflict() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);

            when(lockingPort.acquireLock(anyString())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> createOrderFacade.createOrder(userId, productQuantities))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);

            verify(lockingPort).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
            verify(createOrderUseCase, never()).execute(anyLong(), anyMap());
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void createOrder_UserNotFound() {
            // given
            Long userId = 999L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(createOrderUseCase.execute(userId, productQuantities))
                .thenThrow(new UserException.InvalidUser());
            
            // when & then
            assertThatThrownBy(() -> createOrderFacade.createOrder(userId, productQuantities))
                .isInstanceOf(UserException.InvalidUser.class);
                
            verify(lockingPort).acquireLock(anyString());
            verify(lockingPort).releaseLock(anyString());
            verify(createOrderUseCase).execute(userId, productQuantities);
        }
        
        @Test
        @DisplayName("실패 - 빈 상품 목록")
        void createOrder_EmptyProducts() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(createOrderUseCase.execute(userId, productQuantities))
                .thenThrow(new OrderException.EmptyItems());
            
            // when & then
            assertThatThrownBy(() -> createOrderFacade.createOrder(userId, productQuantities))
                .isInstanceOf(OrderException.EmptyItems.class);
                
            verify(lockingPort).acquireLock(anyString());
            verify(lockingPort).releaseLock(anyString());
            verify(createOrderUseCase).execute(userId, productQuantities);
        }
        
        @Test
        @DisplayName("실패 - 재고 부족")
        void createOrder_OutOfStock() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 100);
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(createOrderUseCase.execute(userId, productQuantities))
                .thenThrow(new ProductException.OutOfStock());
            
            // when & then
            assertThatThrownBy(() -> createOrderFacade.createOrder(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class);
                
            verify(lockingPort).acquireLock(anyString());
            verify(lockingPort).releaseLock(anyString());
            verify(createOrderUseCase).execute(userId, productQuantities);
        }
    }
}