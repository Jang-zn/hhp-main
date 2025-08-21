package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.util.TestBuilder;
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

/**
 * CompleteOrderUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 주문 완료 처리의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 주문 완료 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("주문 완료 처리 비즈니스 시나리오")
class CompleteOrderUseCaseTest {

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private ProductRepositoryPort productRepositoryPort;
    @Mock private OrderItemRepositoryPort orderItemRepositoryPort;
    @Mock private CachePort cachePort;
    @Mock private KeyGenerator keyGenerator;
    
    private CompleteOrderUseCase completeOrderUseCase;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        completeOrderUseCase = new CompleteOrderUseCase(productRepositoryPort, orderItemRepositoryPort, cachePort, keyGenerator);
        
        when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("예약된 재고가 있는 주문을 성공적으로 완료 처리할 수 있다")
    void canCompleteOrderWithReservedStock() {
        // Given
        Order pendingOrder = TestBuilder.OrderBuilder.defaultOrder()
            .id(1L).userId(1L).status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("100000")).build();
            
        Product product = TestBuilder.ProductBuilder.partiallyReservedProduct()
            .id(1L).name("예약상품").price(new BigDecimal("50000"))
            .stock(10).reservedStock(2).build();
                
        OrderItem orderItem = createOrderItem(pendingOrder.getId(), product.getId(), 2);
        
        when(orderItemRepositoryPort.findByOrderId(pendingOrder.getId())).thenReturn(List.of(orderItem));
        when(productRepositoryPort.findById(product.getId())).thenReturn(Optional.of(product));
        
        // When
        completeOrderUseCase.execute(pendingOrder);
        
        // Then
        verify(orderItemRepositoryPort).findByOrderId(pendingOrder.getId());
        verify(productRepositoryPort).findById(product.getId());
        verify(productRepositoryPort).save(any(Product.class));
    }

    @Test
    @DisplayName("주문 항목이 없는 주문도 안전하게 완료 처리된다")
    void safelyCompletesOrderWithoutItems() {
        // Given
        Order emptyOrder = TestBuilder.OrderBuilder.defaultOrder()
            .id(2L).userId(1L).build();
        
        when(orderItemRepositoryPort.findByOrderId(emptyOrder.getId())).thenReturn(List.of());
        
        // When
        completeOrderUseCase.execute(emptyOrder);
        
        // Then
        verify(orderItemRepositoryPort).findByOrderId(emptyOrder.getId());
        verify(productRepositoryPort, never()).findById(any());
        verify(productRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("null 주문에 대한 완료 처리 시도는 예외가 발생한다")
    void throwsExceptionForNullOrder() {
        // When & Then
        assertThatThrownBy(() -> completeOrderUseCase.execute(null))
            .isInstanceOf(NullPointerException.class);
            
        verify(productRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 상품을 포함한 주문 완료 시도는 차단된다")
    void preventsCompletionForOrderWithNonExistentProduct() {
        // Given
        Order orderWithMissingProduct = TestBuilder.OrderBuilder.defaultOrder()
            .id(3L).userId(1L).build();
            
        OrderItem invalidOrderItem = createOrderItem(
            orderWithMissingProduct.getId(), 999L, 1); // 존재하지 않는 상품 ID
        
        when(orderItemRepositoryPort.findByOrderId(orderWithMissingProduct.getId()))
            .thenReturn(List.of(invalidOrderItem));
        when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> completeOrderUseCase.execute(orderWithMissingProduct))
            .isInstanceOf(ProductException.NotFound.class);
                
        verify(orderItemRepositoryPort).findByOrderId(orderWithMissingProduct.getId());
        verify(productRepositoryPort).findById(999L);
        verify(productRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("복수 상품이 포함된 주문을 모두 완료 처리할 수 있다")
    void canCompleteOrderWithMultipleProducts() {
        // Given
        Order multiProductOrder = TestBuilder.OrderBuilder.defaultOrder()
            .id(4L).userId(1L).build();
            
        Product product1 = TestBuilder.ProductBuilder.partiallyReservedProduct()
            .id(1L).stock(10).reservedStock(1).build();
        Product product2 = TestBuilder.ProductBuilder.partiallyReservedProduct()
            .id(2L).stock(5).reservedStock(2).build();
            
        OrderItem orderItem1 = createOrderItem(multiProductOrder.getId(), 1L, 1);
        OrderItem orderItem2 = createOrderItem(multiProductOrder.getId(), 2L, 2);
        
        when(orderItemRepositoryPort.findByOrderId(multiProductOrder.getId()))
            .thenReturn(List.of(orderItem1, orderItem2));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepositoryPort.findById(2L)).thenReturn(Optional.of(product2));
        
        // When
        completeOrderUseCase.execute(multiProductOrder);
        
        // Then
        verify(orderItemRepositoryPort).findByOrderId(multiProductOrder.getId());
        verify(productRepositoryPort).findById(1L);
        verify(productRepositoryPort).findById(2L);
        verify(productRepositoryPort, times(2)).save(any(Product.class));
    }

    @Test
    @DisplayName("주문 완료 처리에서 재고 확정 로직이 올바르게 동작한다")
    void correctlyProcessesStockConfirmation() {
        // Given
        Order orderForStockConfirmation = TestBuilder.OrderBuilder.defaultOrder()
            .id(5L).userId(1L).build();
            
        Product productWithReservedStock = TestBuilder.ProductBuilder.partiallyReservedProduct()
            .id(1L).stock(8).reservedStock(3).build(); // 예약재고 3개
            
        OrderItem orderItem = createOrderItem(
            orderForStockConfirmation.getId(), 1L, 3);
        
        when(orderItemRepositoryPort.findByOrderId(orderForStockConfirmation.getId()))
            .thenReturn(List.of(orderItem));
        when(productRepositoryPort.findById(1L))
            .thenReturn(Optional.of(productWithReservedStock));
        
        // When
        completeOrderUseCase.execute(orderForStockConfirmation);
        
        // Then - 재고 확정 로직이 호출되었는지 검증
        verify(productRepositoryPort).save(any(Product.class));
        assertThat(productWithReservedStock).isNotNull();
    }

    // === 헬퍼 메서드 ===

    private OrderItem createOrderItem(Long orderId, Long productId, int quantity) {
        return OrderItem.builder()
            .id(System.nanoTime()) // 고유 ID
            .orderId(orderId)
            .productId(productId)
            .quantity(quantity)
            .price(new BigDecimal("50000"))
            .build();
    }
}