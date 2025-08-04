package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("CompleteOrderUseCase 단위 테스트")
class CompleteOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private OrderItemRepositoryPort orderItemRepositoryPort;
    
    private CompleteOrderUseCase completeOrderUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        completeOrderUseCase = new CompleteOrderUseCase(productRepositoryPort, orderItemRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .userId(testUser.getId())
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50000"))
            .build();
        
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("성공 - 주문 완료 처리")
    void execute_Success() {
        // given
        Product product = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(new BigDecimal("50000"))
                .stock(10)
                .reservedStock(2)
                .build();
                
        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .orderId(testOrder.getId())
                .productId(product.getId())
                .quantity(2)
                .price(product.getPrice())
                .build();
        
        when(orderItemRepositoryPort.findByOrderId(testOrder.getId())).thenReturn(List.of(orderItem));
        when(productRepositoryPort.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        completeOrderUseCase.execute(testOrder);
        
        // then
        verify(orderItemRepositoryPort).findByOrderId(testOrder.getId());
        verify(productRepositoryPort).findById(product.getId());
        verify(productRepositoryPort).save(any(Product.class));
        assertThat(testOrder).isNotNull();
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 주문")
    void execute_OrderNotFound() {
        // given
        Order nullOrder = null;
        
        // when & then
        assertThatThrownBy(() -> completeOrderUseCase.execute(nullOrder))
            .isInstanceOf(NullPointerException.class);
            
        verify(productRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("성공 - OrderItem이 없는 경우")
    void execute_EmptyOrderItems() {
        // given
        when(orderItemRepositoryPort.findByOrderId(testOrder.getId())).thenReturn(List.of());
        
        // when
        completeOrderUseCase.execute(testOrder);
        
        // then
        verify(orderItemRepositoryPort).findByOrderId(testOrder.getId());
        verify(productRepositoryPort, never()).findById(any());
        verify(productRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 상품을 찾을 수 없는 경우")
    void execute_ProductNotFound() {
        // given
        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .orderId(testOrder.getId())
                .productId(999L)
                .quantity(2)
                .price(new BigDecimal("50000"))
                .build();
        
        when(orderItemRepositoryPort.findByOrderId(testOrder.getId())).thenReturn(List.of(orderItem));
        when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> completeOrderUseCase.execute(testOrder))
                .isInstanceOf(ProductException.NotFound.class);
                
        verify(orderItemRepositoryPort).findByOrderId(testOrder.getId());
        verify(productRepositoryPort).findById(999L);
        verify(productRepositoryPort, never()).save(any());
    }
}