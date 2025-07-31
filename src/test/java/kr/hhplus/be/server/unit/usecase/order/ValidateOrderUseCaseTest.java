package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.ValidateOrderUseCase;
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

@DisplayName("ValidateOrderUseCase 단위 테스트")
class ValidateOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private ValidateOrderUseCase validateOrderUseCase;
    
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validateOrderUseCase = new ValidateOrderUseCase(orderRepositoryPort, userRepositoryPort);
        
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
    @DisplayName("성공 - 유효한 주문 검증")
    void execute_ValidOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(testOrder));
        
        // when
        Order result = validateOrderUseCase.execute(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getUser().getId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
    
    @Test
    @DisplayName("실패 - 사용자를 찾을 수 없음")
    void execute_UserNotFound_ThrowsException() {
        // given
        Long orderId = 1L;
        Long userId = 999L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, userId))
            .isInstanceOf(UserException.UserNotFound.class);
            
        verify(orderRepositoryPort, never()).findById(any());
    }
    
    @Test
    @DisplayName("실패 - 주문을 찾을 수 없음")
    void execute_OrderNotFound_ThrowsException() {
        // given
        Long orderId = 999L;
        Long userId = 1L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, userId))
            .isInstanceOf(OrderException.OrderNotFound.class);
    }
    
    @Test
    @DisplayName("실패 - 주문 소유자가 다름")
    void execute_OrderOwnerMismatch_ThrowsException() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        User otherUser = User.builder().id(2L).name("Other User").build();
        Order otherUserOrder = Order.builder()
            .id(1L)
            .user(otherUser)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50000"))
            .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(otherUserOrder));
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, userId))
            .isInstanceOf(OrderException.OrderAccessDenied.class);
    }
    
    @Test
    @DisplayName("실패 - 이미 결제된 주문")
    void execute_AlreadyPaidOrder_ThrowsException() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        Order paidOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.COMPLETED)
            .totalAmount(new BigDecimal("50000"))
            .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(paidOrder));
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, userId))
            .isInstanceOf(OrderException.OrderAlreadyPaid.class);
    }
}