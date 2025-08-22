package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
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
    private OrderItemRepositoryPort orderItemRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;

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
            orderItemRepositoryPort,
            cachePort,
            keyGenerator
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
            
        testOrder = Order.builder()
            .id(1L)
            .userId(1L)
            .totalAmount(new BigDecimal("50000"))
            .build();
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessCases {
        
        @Test
        @DisplayName("단일 상품으로 주문 생성이 성공한다")
        void createOrder_WithSingleProduct_Success() {
            // given
            Long userId = 1L;
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 2)
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L))).thenReturn(List.of(testProduct));
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            Order result = createOrderUseCase.execute(userId, productQuantities);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(userId);
            
            verify(userRepositoryPort).existsById(userId);
            verify(productRepositoryPort).findByIds(List.of(1L));
            verify(productRepositoryPort).save(testProduct);
            verify(orderRepositoryPort).save(any(Order.class));
            verify(orderItemRepositoryPort).saveAll(any());
        }
        
        @Test
        @DisplayName("여러 상품으로 주문 생성이 성공한다")
        void createOrder_WithMultipleProducts_Success() {
            // given
            Long userId = 1L;
            Product secondProduct = Product.builder()
                .id(2L)
                .name("Second Product")
                .price(new BigDecimal("30000"))
                .stock(50)
                .build();
                
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 2),
                new ProductQuantityDto(2L, 1)
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L, 2L)))
                .thenReturn(List.of(testProduct, secondProduct));
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            Order result = createOrderUseCase.execute(userId, productQuantities);
            
            // then
            assertThat(result).isNotNull();
            verify(productRepositoryPort).save(testProduct);
            verify(productRepositoryPort).save(secondProduct);
            verify(orderItemRepositoryPort).saveAll(argThat(items -> {
                List<OrderItem> itemList = (List<OrderItem>) items;
                return itemList.size() == 2;
            }));
        }
    }
    
    @Nested
    @DisplayName("실패 케이스")
    class FailureCases {
        
        @Test
        @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다")
        void createOrder_WithNullUserId_ThrowsException() {
            // given
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 1)
            );
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(null, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
        }
        
        @Test
        @DisplayName("빈 상품 목록으로 주문 생성 시 OrderException.EmptyItems가 발생한다")
        void createOrder_WithEmptyProducts_ThrowsEmptyItemsException() {
            // given
            Long userId = 1L;
            List<ProductQuantityDto> emptyProductQuantities = List.of();
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, emptyProductQuantities))
                .isInstanceOf(OrderException.EmptyItems.class);
        }
        
        @Test
        @DisplayName("null 상품 목록으로 주문 생성 시 OrderException.EmptyItems가 발생한다")
        void createOrder_WithNullProducts_ThrowsEmptyItemsException() {
            // given
            Long userId = 1L;
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, null))
                .isInstanceOf(OrderException.EmptyItems.class);
        }
        
        @Test
        @DisplayName("존재하지 않는 사용자로 주문 생성 시 UserException.NotFound가 발생한다")
        void createOrder_WithNonExistentUser_ThrowsUserNotFoundException() {
            // given
            Long nonExistentUserId = 999L;
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 1)
            );
            
            when(userRepositoryPort.existsById(nonExistentUserId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(nonExistentUserId, productQuantities))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(userRepositoryPort).existsById(nonExistentUserId);
        }
        
        @Test
        @DisplayName("존재하지 않는 상품으로 주문 생성 시 ProductException.NotFound가 발생한다")
        void createOrder_WithNonExistentProduct_ThrowsProductNotFoundException() {
            // given
            Long userId = 1L;
            Long nonExistentProductId = 999L;
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(nonExistentProductId, 1)
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(nonExistentProductId)))
                .thenReturn(List.of()); // 빈 목록 반환
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.NotFound.class);
        }
        
        @Test
        @DisplayName("재고가 부족한 상품으로 주문 생성 시 ProductException.OutOfStock이 발생한다")
        void createOrder_WithInsufficientStock_ThrowsOutOfStockException() {
            // given
            Long userId = 1L;
            Product lowStockProduct = Product.builder()
                .id(1L)
                .name("Low Stock Product")
                .price(new BigDecimal("50000"))
                .stock(1)
                .build();
                
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 5) // 재고보다 많은 수량 요청
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L))).thenReturn(List.of(lowStockProduct));
            
            // when & then
            assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class);
        }
    }
    
    @Nested
    @DisplayName("비즈니스 로직 검증")
    class BusinessLogicValidation {
        
        @Test
        @DisplayName("주문 생성 시 상품 재고가 올바르게 예약된다")
        void createOrder_ReservesStockCorrectly() {
            // given
            Long userId = 1L;
            int orderQuantity = 3;
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, orderQuantity)
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L))).thenReturn(List.of(testProduct));
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            createOrderUseCase.execute(userId, productQuantities);
            
            // then
            verify(productRepositoryPort).save(argThat(product -> 
                product.getReservedStock() == orderQuantity
            ));
        }
        
        @Test
        @DisplayName("주문 총액이 올바르게 계산된다")
        void createOrder_CalculatesTotalAmountCorrectly() {
            // given
            Long userId = 1L;
            Product secondProduct = Product.builder()
                .id(2L)
                .name("Second Product")
                .price(new BigDecimal("20000"))
                .stock(50)
                .build();
                
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, 2), // 50000 * 2 = 100000
                new ProductQuantityDto(2L, 3)  // 20000 * 3 = 60000
            );
            // 총액: 160000
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L, 2L)))
                .thenReturn(List.of(testProduct, secondProduct));
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            createOrderUseCase.execute(userId, productQuantities);
            
            // then
            verify(orderRepositoryPort).save(argThat(order -> 
                order.getTotalAmount().equals(new BigDecimal("160000"))
            ));
        }
        
        @Test
        @DisplayName("OrderItem이 올바른 정보로 생성된다")
        void createOrder_CreatesOrderItemsCorrectly() {
            // given
            Long userId = 1L;
            int quantity = 2;
            List<ProductQuantityDto> productQuantities = List.of(
                new ProductQuantityDto(1L, quantity)
            );
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(productRepositoryPort.findByIds(List.of(1L))).thenReturn(List.of(testProduct));
            when(orderRepositoryPort.save(any(Order.class))).thenReturn(testOrder);
            
            // when
            createOrderUseCase.execute(userId, productQuantities);
            
            // then
            verify(orderItemRepositoryPort).saveAll(argThat(items -> {
                List<OrderItem> itemList = (List<OrderItem>) items;
                if (itemList.size() != 1) return false;
                OrderItem item = itemList.get(0);
                return item.getProductId().equals(1L) &&
                       item.getQuantity() == quantity &&
                       item.getPrice().equals(testProduct.getPrice()) &&
                       item.getOrderId().equals(1L);
            }));
        }
    }
}