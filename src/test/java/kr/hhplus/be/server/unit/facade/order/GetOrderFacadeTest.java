package kr.hhplus.be.server.unit.facade.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.facade.order.GetOrderFacade;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CheckOrderAccessUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetOrderFacade 단위 테스트")
class GetOrderFacadeTest {

    @Mock
    private GetOrderUseCase getOrderUseCase;
    
    @Mock
    private CheckOrderAccessUseCase checkOrderAccessUseCase;
    
    private GetOrderFacade getOrderFacade;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderFacade = new GetOrderFacade(getOrderUseCase, checkOrderAccessUseCase);
        
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
            .productId(product.getId())
            .quantity(2)
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .userId(testUser.getId())
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("100000"))
            .build();
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {
        
        @Test
        @DisplayName("성공 - 정상 주문 조회")
        void getOrder_Success() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
           
            when(checkOrderAccessUseCase.execute(userId, orderId)).thenReturn(testOrder);
            when(getOrderUseCase.execute(userId, orderId)).thenReturn(Optional.of(testOrder));
            
            // when
            Order result = getOrderFacade.getOrder(orderId, userId);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            
            verify(checkOrderAccessUseCase).execute(userId, orderId);
            verify(getOrderUseCase).execute(userId, orderId);
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 주문")
        void getOrder_OrderNotFound() {
            // given
            Long orderId = 999L;
            Long userId = 1L;
            
            doThrow(new OrderException.NotFound())
                .when(checkOrderAccessUseCase).execute(userId, orderId);
            
            // when & then
            assertThatThrownBy(() -> getOrderFacade.getOrder(orderId, userId))
                .isInstanceOf(OrderException.NotFound.class);
            verify(checkOrderAccessUseCase).execute(userId, orderId);
            verify(getOrderUseCase, never()).execute(any(), any());
        }
        
        @Test
        @DisplayName("실패 - 주문 접근 권한 없음")
        void getOrder_AccessDenied() {
            // given
            Long orderId = 1L;
            Long userId = 999L;
            
            doThrow(new OrderException.Unauthorized())
                .when(checkOrderAccessUseCase).execute(userId, orderId);
            
            // when & then
            assertThatThrownBy(() -> getOrderFacade.getOrder(orderId, userId))
                .isInstanceOf(OrderException.Unauthorized.class);
                
            verify(checkOrderAccessUseCase).execute(userId, orderId);
            verify(getOrderUseCase, never()).execute(any(), any());
        }
    }
}