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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CompleteOrderUseCase 단위 테스트")
class CompleteOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    private CompleteOrderUseCase completeOrderUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        completeOrderUseCase = new CompleteOrderUseCase(orderRepositoryPort);
        
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
    }

    @Test
    @DisplayName("성공 - 주문 완료 처리")
    void execute_Success() {
        // given
        Long orderId = 1L;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Order result = completeOrderUseCase.execute(orderId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepositoryPort).save(testOrder);
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 주문")
    void execute_OrderNotFound() {
        // given
        Long orderId = 999L;
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> completeOrderUseCase.execute(orderId))
            .isInstanceOf(OrderException.NotFound.class);
            
        verify(orderRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 이미 완료된 주문")
    void execute_AlreadyCompleted() {
        // given
        Long orderId = 1L;
        
        Order completedOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.COMPLETED)
            .totalAmount(new BigDecimal("50000"))
            .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(completedOrder));
        
        // when & then
        assertThatThrownBy(() -> completeOrderUseCase.execute(orderId))
            .isInstanceOf(OrderException.AlreadyCompleted.class);
            
        verify(orderRepositoryPort, never()).save(any());
    }
}