package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import kr.hhplus.be.server.domain.usecase.order.CheckOrderAccessUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ValidateCouponUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;

@DisplayName("OrderController 단위 테스트")
class OrderControllerTest {

    private OrderController orderController;
    
    @Mock
    private CreateOrderUseCase createOrderUseCase;
    
    @Mock
    private PayOrderUseCase payOrderUseCase;
    
    @Mock
    private GetOrderUseCase getOrderUseCase;
    
    @Mock
    private GetOrderListUseCase getOrderListUseCase;
    
    @Mock
    private CheckOrderAccessUseCase checkOrderAccessUseCase;
    
    @Mock
    private ValidateCouponUseCase validateCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderController = new OrderController(createOrderUseCase, payOrderUseCase, getOrderUseCase, getOrderListUseCase, checkOrderAccessUseCase, validateCouponUseCase);
    }


    @Nested
    @DisplayName("주문 생성 테스트")
    class CreateOrderTests {
        
        // Test data providers for this nested class
        static Stream<Arguments> provideOrderData() {
            return Stream.of(
                    Arguments.of(1L, List.of(new OrderRequest.ProductQuantity(1L, 2)), List.of(1L)), // 단일 상품, 단일 쿠폰
                    Arguments.of(2L, List.of(new OrderRequest.ProductQuantity(1L, 1), new OrderRequest.ProductQuantity(2L, 3)), List.of()), // 다중 상품, 쿠폰 없음
                    Arguments.of(3L, List.of(new OrderRequest.ProductQuantity(3L, 1)), List.of(1L, 2L)) // 단일 상품, 다중 쿠폰
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 주문 생성")
        void createOrder_Success() {
            // given
            Long userId = 1L;
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 2),
                new OrderRequest.ProductQuantity(2L, 1)
            );
            List<Long> couponIds = List.of(1L);
            OrderRequest request = new OrderRequest(userId, null, couponIds);
            request.setProducts(products);

            Order order = createMockOrder(userId, "테스트 사용자", new BigDecimal("100000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(order);

            // when
            OrderResponse response = orderController.createOrder(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.status()).isEqualTo("PENDING");
        }

        @ParameterizedTest
        @MethodSource("provideOrderData")
        @DisplayName("성공케이스: 다양한 주문 데이터로 주문 생성")
        void createOrder_WithDifferentData(Long userId, List<OrderRequest.ProductQuantity> products, List<Long> couponIds) {
            // given
            OrderRequest request = new OrderRequest(userId, null, couponIds);
            request.setProducts(products);
            
            Order order = createMockOrder(userId, "테스트 사용자", new BigDecimal("50000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(order);

            // when
            OrderResponse response = orderController.createOrder(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("성공케이스: 기존 productIds 필드 사용 (하위 호환성)")
        void createOrder_WithLegacyProductIds() {
            // given
            Long userId = 1L;
            List<Long> productIds = List.of(1L, 2L);
            List<Long> couponIds = List.of(1L);
            OrderRequest request = new OrderRequest(userId, productIds, couponIds);
            
            Order order = createMockOrder(userId, "테스트 사용자", new BigDecimal("100000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(order);

            // when
            OrderResponse response = orderController.createOrder(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자로 주문 생성")
        void createOrder_UserNotFound() {
            // given
            Long userId = 999L;
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 1)
            );
            List<Long> couponIds = List.of();
            OrderRequest request = new OrderRequest(userId, null, couponIds);
            request.setProducts(products);

            when(createOrderUseCase.execute(anyLong(), anyMap()))
                    .thenThrow(new UserException.InvalidUser());

            // when & then
            assertThatThrownBy(() -> orderController.createOrder(request))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @Test
        @DisplayName("실패케이스: 빈 상품 리스트로 주문 생성")
        void createOrder_EmptyProductList() {
            // given
            Long userId = 1L;
            List<OrderRequest.ProductQuantity> products = Collections.emptyList();
            List<Long> couponIds = List.of();
            OrderRequest request = new OrderRequest(userId, null, couponIds);
            request.setProducts(products);

            when(createOrderUseCase.execute(anyLong(), anyMap()))
                    .thenThrow(new OrderException.EmptyItems());

            // when & then
            assertThatThrownBy(() -> orderController.createOrder(request))
                    .isInstanceOf(OrderException.EmptyItems.class)
                    .hasMessage(ErrorCode.INVALID_ORDER_ITEMS.getMessage());
        }

        @Test
        @DisplayName("실패케이스: 재고 부족 상품으로 주문 생성")
        void createOrder_InsufficientStock() {
            // given
            Long userId = 1L;
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 5)
            );
            List<Long> couponIds = List.of();
            OrderRequest request = new OrderRequest(userId, null, couponIds);
            request.setProducts(products);

            when(createOrderUseCase.execute(anyLong(), anyMap()))
                    .thenThrow(new ProductException.OutOfStock());

            // when & then
            assertThatThrownBy(() -> orderController.createOrder(request))
                    .isInstanceOf(ProductException.OutOfStock.class)
                    .hasMessage(ErrorCode.PRODUCT_OUT_OF_STOCK.getMessage());
        }
    }

    @Nested
    @DisplayName("주문 결제 테스트")
    class PayOrderTests {
        
        // Test data providers for this nested class
        static Stream<Arguments> provideOrderIds() {
            return Stream.of(
                    Arguments.of(1L),
                    Arguments.of(100L),
                    Arguments.of(999L)
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 주문 결제")
        void payOrder_Success() {
            // given
            Long orderId = 1L;
            
            Order order = createMockOrder(1L, "테스트 사용자", new BigDecimal("100000"));
            order.setId(orderId);
            
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(new BigDecimal("100000"))
                    .status(PaymentStatus.PAID)
                    .build();
            payment.setId(1L);
            
            when(payOrderUseCase.execute(orderId, 1L, null)).thenReturn(payment);

            // when
            OrderRequest request = new OrderRequest(1L, null);
            PaymentResponse response = orderController.payOrder(orderId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo("PAID");
        }

        @ParameterizedTest
        @MethodSource("provideOrderIds")
        @DisplayName("성공케이스: 다양한 주문 ID로 결제")
        void payOrder_WithDifferentOrderIds(Long orderId) {
            // given
            Order order = createMockOrder(1L, "테스트 사용자", new BigDecimal("75000"));
            order.setId(orderId);
            
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(new BigDecimal("75000"))
                    .status(PaymentStatus.PAID)
                    .build();
            payment.setId(1L);
            
            when(payOrderUseCase.execute(orderId, 1L, null)).thenReturn(payment);
            
            // when
            OrderRequest request = new OrderRequest(1L, null);
            PaymentResponse response = orderController.payOrder(orderId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 주문 결제")
        void payOrder_OrderNotFound() {
            // given
            Long orderId = 999L;
            
            when(payOrderUseCase.execute(orderId, null, null))
                    .thenThrow(new OrderException.NotFound());

            // when & then
            OrderRequest request = new OrderRequest(null, null);
            assertThatThrownBy(() -> orderController.payOrder(orderId, request))
                    .isInstanceOf(OrderException.NotFound.class)
                    .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패케이스: 잔액 부족으로 결제")
        void payOrder_InsufficientBalance() {
            // given
            Long orderId = 1L;
            
            when(payOrderUseCase.execute(orderId, null, null))
                    .thenThrow(new BalanceException.InsufficientBalance());

            // when & then
            OrderRequest request = new OrderRequest(null, null);
            assertThatThrownBy(() -> orderController.payOrder(orderId, request))
                    .isInstanceOf(BalanceException.InsufficientBalance.class)
                    .hasMessage(ErrorCode.INSUFFICIENT_BALANCE.getMessage());
        }
    }

    @Nested
    @DisplayName("수량 처리 테스트")
    class QuantityHandlingTests {
        
        @Test
        @DisplayName("성공케이스: products 필드로 수량 정보 전달 확인")
        void testProductQuantitiesArePassedCorrectly() {
            // given
            Long userId = 1L;
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 2),
                new OrderRequest.ProductQuantity(2L, 3),
                new OrderRequest.ProductQuantity(3L, 1)
            );
            
            OrderRequest request = new OrderRequest();
            request.setUserId(userId);
            request.setProducts(products);
            
            Order mockOrder = createMockOrder(userId, "테스트 사용자", new BigDecimal("100000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(mockOrder);
            
            // when
            orderController.createOrder(request);
            
            // then
            ArgumentCaptor<Map<Long, Integer>> quantitiesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(createOrderUseCase).execute(anyLong(), quantitiesCaptor.capture());
            
            Map<Long, Integer> capturedQuantities = quantitiesCaptor.getValue();
            assertThat(capturedQuantities).hasSize(3);
            assertThat(capturedQuantities.get(1L)).isEqualTo(2);
            assertThat(capturedQuantities.get(2L)).isEqualTo(3);
            assertThat(capturedQuantities.get(3L)).isEqualTo(1);
        }

        @Test
        @DisplayName("성공케이스: 기존 productIds 필드로 기본 수량 1 설정 확인")
        void testLegacyProductIdsDefaultToQuantityOne() {
            // given
            Long userId = 1L;
            List<Long> productIds = List.of(1L, 2L, 3L);
            
            OrderRequest request = new OrderRequest();
            request.setUserId(userId);
            request.setProductIds(productIds);
            
            Order mockOrder = createMockOrder(userId, "테스트 사용자", new BigDecimal("100000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(mockOrder);
            
            // when
            orderController.createOrder(request);
            
            // then
            ArgumentCaptor<Map<Long, Integer>> quantitiesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(createOrderUseCase).execute(anyLong(), quantitiesCaptor.capture());
            
            Map<Long, Integer> capturedQuantities = quantitiesCaptor.getValue();
            assertThat(capturedQuantities).hasSize(3);
            assertThat(capturedQuantities.get(1L)).isEqualTo(1);
            assertThat(capturedQuantities.get(2L)).isEqualTo(1);
            assertThat(capturedQuantities.get(3L)).isEqualTo(1);
        }

        @Test
        @DisplayName("성공케이스: products 필드 우선순위 확인")
        void testProductsFieldTakesPriorityOverProductIds() {
            // given
            Long userId = 1L;
            List<Long> productIds = List.of(1L, 2L); // 기존 방식
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 5) // 새로운 방식
            );
            
            OrderRequest request = new OrderRequest();
            request.setUserId(userId);
            request.setProductIds(productIds);
            request.setProducts(products);
            
            Order mockOrder = createMockOrder(userId, "테스트 사용자", new BigDecimal("100000"));
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(mockOrder);
            
            // when
            orderController.createOrder(request);
            
            // then
            ArgumentCaptor<Map<Long, Integer>> quantitiesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(createOrderUseCase).execute(anyLong(), quantitiesCaptor.capture());
            
            Map<Long, Integer> capturedQuantities = quantitiesCaptor.getValue();
            assertThat(capturedQuantities).hasSize(1);
            assertThat(capturedQuantities.get(1L)).isEqualTo(5); // products 필드의 수량이 사용됨
        }
    }

    @Nested
    @DisplayName("엔티티 상태 테스트")
    class EntityStatusTests {
        
        @Test
        @DisplayName("성공케이스: Order 객체 생성 및 상태 확인")
        void testOrderCreationAndStatus() {
            // given
            User user = User.builder().name("테스트 사용자").build();
            
            // when
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("100000"))
                    .build();
            
            // then
            assertThat(order).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getStatus().name()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("성공케이스: Order 상태 필드 테스트")
        void testOrderStatusField() {
            // given
            Long userId = 1L;
            List<OrderRequest.ProductQuantity> products = List.of(
                new OrderRequest.ProductQuantity(1L, 2)
            );
            
            OrderRequest request = new OrderRequest();
            request.setUserId(userId);
            request.setProducts(products);
            
            User user = User.builder().name("테스트 사용자").build();
            user.setId(userId);
            
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("50000"))
                    .build();
            product.setId(1L);
            
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(2)
                    .build();
            
            List<OrderItem> orderItems = new ArrayList<>();
            orderItems.add(orderItem);
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("100000"))
                    .items(orderItems)
                    .build();
            
            when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(order);
            
            // when
            OrderResponse response = orderController.createOrder(request);
            
            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTests {
        
        // Test data providers for this nested class
        static Stream<Arguments> provideInvalidOrderIds() {
            return Stream.of(
                    Arguments.of(-1L),
                    Arguments.of(0L),
                    Arguments.of(Long.MAX_VALUE)
            );
        }
        
        @Test
        @DisplayName("실패케이스: null 요청으로 주문 생성")
        void createOrder_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> orderController.createOrder(null))
                    .isInstanceOf(CommonException.InvalidRequest.class)
                    .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
        }

        @Test
        @DisplayName("실패케이스: null 주문 ID로 결제")
        void payOrder_WithNullOrderId() {
            // when & then
            OrderRequest request = new OrderRequest(null, null);
            assertThatThrownBy(() -> orderController.payOrder(null, request))
                    .isInstanceOf(OrderException.OrderIdCannotBeNull.class)
                    .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
        }

        @ParameterizedTest
        @MethodSource("provideInvalidOrderIds")
        @DisplayName("실패케이스: 비정상 주문 ID로 결제")
        void payOrder_WithInvalidOrderIds(Long invalidOrderId) {
            // given
            when(payOrderUseCase.execute(invalidOrderId, null, null))
                    .thenThrow(new OrderException.NotFound());

            // when & then
            OrderRequest request = new OrderRequest(null, null);
            assertThatThrownBy(() -> orderController.payOrder(invalidOrderId, request))
                    .isInstanceOf(OrderException.NotFound.class)
                    .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());
        }
    }

    // Helper method
    private Order createMockOrder(Long userId, String userName, BigDecimal totalAmount) {
        User user = User.builder().name(userName).build();
        user.setId(userId);
        
        Product product = Product.builder()
                .name("테스트 상품")
                .price(new BigDecimal("50000"))
                .build();
        product.setId(1L);
        
        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .quantity(2)
                .build();
        
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(orderItem);
        
        return Order.builder()
                .user(user)
                .totalAmount(totalAmount)
                .items(orderItems)
                .build();
    }
}