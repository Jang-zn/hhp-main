package kr.hhplus.be.server.unit.facade.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.facade.order.CreateOrderFacade;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CreateOrderFacade 비즈니스 시나리오 테스트
 * 
 * Why: 주문 생성 파사드의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 고객의 주문 생성 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("주문 생성 파사드 비즈니스 시나리오")
class CreateOrderFacadeTest {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private LockingPort lockingPort;
    
    private CreateOrderFacade createOrderFacade;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderFacade = new CreateOrderFacade(createOrderUseCase, lockingPort);
    }

    @Test
    @DisplayName("고객이 복수 상품으로 정상적인 주문을 생성할 수 있다")
    void customerCanCreateOrderWithMultipleProducts() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<ProductQuantityDto> productQuantities = List.of(
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity()
                .productId(1L).quantity(2).build(),
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity()
                .productId(2L).quantity(1).build()
        );
        Order expectedOrder = TestBuilder.OrderBuilder.defaultOrder()
            .id(1L).userId(customer.getId())
            .totalAmount(new BigDecimal("100000"))
            .build();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(createOrderUseCase.execute(customer.getId(), productQuantities)).thenReturn(expectedOrder);
        
        // When
        Order result = createOrderFacade.createOrder(customer.getId(), productQuantities);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(customer.getId());
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("100000"));
        
        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort).releaseLock(anyString());
        verify(createOrderUseCase).execute(customer.getId(), productQuantities);
    }

    @Test
    @DisplayName("동시 주문 요청에서 락 획득 실패 시 동시성 충돌 예외가 발생한다")
    void throwsConcurrencyConflictWhenLockAcquisitionFails() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<ProductQuantityDto> productQuantities = List.of(
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity()
                .productId(1L).quantity(2).build()
        );

        when(lockingPort.acquireLock(anyString())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> createOrderFacade.createOrder(customer.getId(), productQuantities))
            .isInstanceOf(CommonException.ConcurrencyConflict.class);

        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort, never()).releaseLock(anyString());
        verify(createOrderUseCase, never()).execute(anyLong(), anyList());
    }

    @Test
    @DisplayName("존재하지 않는 고객의 주문 생성 요청은 차단된다")
    void preventsOrderCreationForNonExistentCustomer() {
        // Given
        Long nonExistentCustomerId = 999L;
        List<ProductQuantityDto> productQuantities = List.of(
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity()
                .productId(1L).quantity(2).build()
        );
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(createOrderUseCase.execute(nonExistentCustomerId, productQuantities))
            .thenThrow(new UserException.InvalidUser());
        
        // When & Then
        assertThatThrownBy(() -> createOrderFacade.createOrder(nonExistentCustomerId, productQuantities))
            .isInstanceOf(UserException.InvalidUser.class);
            
        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort).releaseLock(anyString());
        verify(createOrderUseCase).execute(nonExistentCustomerId, productQuantities);
    }

    @Test
    @DisplayName("빈 상품 목록으로 주문 생성 시도는 차단된다")
    void preventsOrderCreationWithEmptyProductList() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<ProductQuantityDto> emptyProductQuantities = List.of();
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(createOrderUseCase.execute(customer.getId(), emptyProductQuantities))
            .thenThrow(new OrderException.EmptyItems());
        
        // When & Then
        assertThatThrownBy(() -> createOrderFacade.createOrder(customer.getId(), emptyProductQuantities))
            .isInstanceOf(OrderException.EmptyItems.class);
            
        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort).releaseLock(anyString());
        verify(createOrderUseCase).execute(customer.getId(), emptyProductQuantities);
    }

    @Test
    @DisplayName("재고 부족 상품으로 주문 생성 시도는 차단된다")
    void preventsOrderCreationForOutOfStockProduct() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<ProductQuantityDto> outOfStockQuantities = List.of(
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity()
                .productId(1L).quantity(100).build() // 과도한 수량
        );
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(createOrderUseCase.execute(customer.getId(), outOfStockQuantities))
            .thenThrow(new ProductException.OutOfStock());
        
        // When & Then
        assertThatThrownBy(() -> createOrderFacade.createOrder(customer.getId(), outOfStockQuantities))
            .isInstanceOf(ProductException.OutOfStock.class);
            
        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort).releaseLock(anyString());
        verify(createOrderUseCase).execute(customer.getId(), outOfStockQuantities);
    }

    @Test
    @DisplayName("주문 생성 중 예외 발생 시에도 락이 정상적으로 해제된다")
    void ensuresLockReleaseEvenWhenExceptionOccurs() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<ProductQuantityDto> productQuantities = List.of(
            TestBuilder.ProductQuantityBuilder.defaultProductQuantity().build()
        );
        
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(createOrderUseCase.execute(customer.getId(), productQuantities))
            .thenThrow(new RuntimeException("Unexpected error"));
        
        // When & Then
        assertThatThrownBy(() -> createOrderFacade.createOrder(customer.getId(), productQuantities))
            .isInstanceOf(RuntimeException.class);
            
        verify(lockingPort).acquireLock(anyString());
        verify(lockingPort).releaseLock(anyString()); // 예외 상황에서도 락 해제 확인
    }
}