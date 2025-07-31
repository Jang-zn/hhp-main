package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

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
    
    private CompleteOrderUseCase completeOrderUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        completeOrderUseCase = new CompleteOrderUseCase(productRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50000"))
            .items(List.of(OrderItem.builder().product(mock(Product.class, withSettings().extraInterfaces(Product.class).defaultAnswer(CALLS_REAL_METHODS))).quantity(1).build()))
            .build();
    }

    @Test
    @DisplayName("성공 - 주문 완료 처리")
    void execute_Success() {
        // given
        
        // when
        completeOrderUseCase.execute(testOrder);
        
        // then
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(productRepositoryPort, atLeastOnce()).save(any(Product.class));
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
    @DisplayName("실패 - 이미 완료된 주문")
    void execute_AlreadyCompleted() {
        // given
        
        Order completedOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.COMPLETED)
            .totalAmount(new BigDecimal("50000"))
            .build();
        
        // when & then
        assertThatThrownBy(() -> completeOrderUseCase.execute(testOrder))
            .isInstanceOf(OrderException.AlreadyPaid.class);
            
        verify(productRepositoryPort, never()).save(any());
    }
}