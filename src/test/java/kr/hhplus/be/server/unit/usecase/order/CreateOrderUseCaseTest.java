package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.List;

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
    
    private CreateOrderUseCase createOrderUseCase;
    
    private User testUser;
    private Product testProduct1;
    private Product testProduct2;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderUseCase = new CreateOrderUseCase(userRepositoryPort, productRepositoryPort, orderRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testProduct1 = Product.builder()
            .id(1L)
            .name("Product 1")
            .price(new BigDecimal("50000"))
            .stock(10)
            .reservedStock(0)
            .build();
            
        testProduct2 = Product.builder()
            .id(2L)
            .name("Product 2")
            .price(new BigDecimal("30000"))
            .stock(5)
            .reservedStock(0)
            .build();
    }

    @Test
    @DisplayName("성공 - 단일 상품 주문 생성")
    void execute_SingleProduct_Success() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 2);
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Order result = createOrderUseCase.execute(userId, productQuantities);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        
        // 재고 예약 확인
        assertThat(testProduct1.getReservedStock()).isEqualTo(2);
        
        verify(productRepositoryPort).save(testProduct1);
        verify(orderRepositoryPort).save(any(Order.class));
    }
    
    @Test
    @DisplayName("성공 - 다중 상품 주문 생성")
    void execute_MultipleProducts_Success() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 2, 2L, 1);
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepositoryPort.findById(2L)).thenReturn(Optional.of(testProduct2));
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Order result = createOrderUseCase.execute(userId, productQuantities);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("130000")); // 50000*2 + 30000*1
        assertThat(result.getItems()).hasSize(2);
        
        // 재고 예약 확인
        assertThat(testProduct1.getReservedStock()).isEqualTo(2);
        assertThat(testProduct2.getReservedStock()).isEqualTo(1);
        
        verify(productRepositoryPort, times(2)).save(any(Product.class));
        verify(orderRepositoryPort).save(any(Order.class));
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 사용자")
    void execute_UserNotFound() {
        // given
        Long userId = 999L;
        Map<Long, Integer> productQuantities = Map.of(1L, 1);
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(productRepositoryPort, never()).save(any());
        verify(orderRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 상품")
    void execute_ProductNotFound() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(999L, 1);
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
            .isInstanceOf(ProductException.NotFound.class);
            
        verify(productRepositoryPort, never()).save(any());
        verify(orderRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 재고 부족")
    void execute_OutOfStock() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 15); // 재고보다 많이 주문
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(testProduct1));
        
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
            .isInstanceOf(ProductException.OutOfStock.class);
            
        verify(productRepositoryPort, never()).save(any());
        verify(orderRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 빈 주문 목록")
    void execute_EmptyProducts() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of();
        
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
            .isInstanceOf(OrderException.EmptyItems.class);
            
        verify(userRepositoryPort, never()).findById(any());
    }
    
    @Test
    @DisplayName("실패 - null 파라미터")
    void execute_NullParameters() {
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(null, Map.of(1L, 1)))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> createOrderUseCase.execute(1L, null))
            .isInstanceOf(OrderException.EmptyItems.class);
    }
    
    @Test
    @DisplayName("실패 - 잘못된 수량 (0 또는 음수)")
    void execute_InvalidQuantity() {
        // given
        Long userId = 1L;
        
        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, Map.of(1L, 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
            
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, Map.of(1L, -1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
    }
}