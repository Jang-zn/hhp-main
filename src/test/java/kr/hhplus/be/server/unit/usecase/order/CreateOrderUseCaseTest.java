package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreateOrderUseCase 단위 테스트")
class CreateOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;
    
    private CreateOrderUseCase createOrderUseCase;
    
    private User testUser;
    private Product testProduct;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderUseCase = new CreateOrderUseCase(
            userRepositoryPort,
            productRepositoryPort, 
            orderRepositoryPort,
            eventLogRepositoryPort,
            lockingPort,
            cachePort
        );
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("50000"))
            .stock(100)
            .build();
            
        testOrder = mock(Order.class);
        when(testOrder.getId()).thenReturn(1L);
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {
        
        @Test
        @DisplayName("성공 - 정상 주문 생성")
        void createOrder_Success() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);
            
            when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepositoryPort.save(any(Product.class))).thenReturn(testProduct);
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            Order result = createOrderUseCase.execute(userId, productQuantities);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(testOrder);
            
            verify(lockingPort).acquireLock("order-creation-" + userId);
            verify(userRepositoryPort).findById(userId);
            verify(productRepositoryPort).findById(1L);
            verify(productRepositoryPort).save(any(Product.class));
            verify(orderRepositoryPort).save(any(Order.class));
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void createOrder_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);
            
            when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            verify(lockingPort).acquireLock("order-creation-" + userId);
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void createOrder_UserNotFound() {
            // given
            Long userId = 999L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);
            
            when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(lockingPort).acquireLock("order-creation-" + userId);
            verify(userRepositoryPort).findById(userId);
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 상품")
        void createOrder_ProductNotFound() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(999L, 2);
            
            when(lockingPort.acquireLock("order-creation-" + userId)).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
            when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.NotFound.class);
                
            verify(lockingPort).acquireLock("order-creation-" + userId);
            verify(userRepositoryPort).findById(userId);
            verify(productRepositoryPort).findById(999L);
            verify(orderRepositoryPort, never()).save(any());
            verify(lockingPort).releaseLock("order-creation-" + userId);
        }
        
        @Test
        @DisplayName("실패 - null 사용자 ID")
        void createOrder_NullUserId() {
            // given
            Long userId = null;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 빈 상품 수량 맵")
        void createOrder_EmptyProductQuantities() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of();
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(OrderException.EmptyItems.class);
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - null 상품 수량 맵")
        void createOrder_NullProductQuantities() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = null;
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(OrderException.EmptyItems.class);
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 잘못된 상품 수량 (0개)")
        void createOrder_InvalidQuantityZero() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 0);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 잘못된 상품 수량 (음수)")
        void createOrder_InvalidQuantityNegative() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, -1);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 잘못된 상품 ID (null)")
        void createOrder_InvalidProductIdNull() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = new java.util.HashMap<>();
            productQuantities.put(null, 2);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid productId: null");
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
        
        @Test
        @DisplayName("실패 - 잘못된 상품 ID (0 이하)")
        void createOrder_InvalidProductIdZeroOrNegative() {
            // given
            Long userId = 1L;
            Map<Long, Integer> productQuantities = Map.of(0L, 2);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid productId: 0");
                
            verify(lockingPort, never()).acquireLock(any());
            verify(userRepositoryPort, never()).findById(any());
            verify(productRepositoryPort, never()).findById(any());
            verify(orderRepositoryPort, never()).save(any());
        }
    }
}