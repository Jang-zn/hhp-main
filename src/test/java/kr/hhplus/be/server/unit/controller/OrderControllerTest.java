package kr.hhplus.be.server.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.CreateOrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.PaymentException;
import kr.hhplus.be.server.domain.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderController = new OrderController(createOrderUseCase, payOrderUseCase);
    }

    @Test
    @DisplayName("주문 생성 API 성공")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(1L, 2L);
        List<Long> couponIds = List.of(1L);
        CreateOrderRequest request = new CreateOrderRequest(userId, productIds, couponIds);
        
        User user = User.builder().name("테스트 사용자").build();
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
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
    @DisplayName("다양한 주문 데이터로 주문 생성")
    void createOrder_WithDifferentData(Long userId, List<Long> productIds, List<Long> couponIds) {
        // given
        CreateOrderRequest request = new CreateOrderRequest(userId, productIds, couponIds);
        
        User user = User.builder().name("테스트 사용자").build();
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .build();
        
        when(createOrderUseCase.execute(anyLong(), anyMap())).thenReturn(order);

        // when
        OrderResponse response = orderController.createOrder(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("주문 결제 API 성공")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        
        User user = User.builder().name("테스트 사용자").build();
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        Payment payment = Payment.builder()
                .order(order)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(payOrderUseCase.execute(orderId, 1L, null)).thenReturn(payment);

        // when
        PaymentResponse response = orderController.payOrder(orderId, 1L, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("provideOrderIds")
    @DisplayName("다양한 주문 ID로 결제")
    void payOrder_WithDifferentOrderIds(Long orderId) {
        // given
        User user = User.builder().name("테스트 사용자").build();
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("75000"))
                .build();
        Payment payment = Payment.builder()
                .order(order)
                .amount(new BigDecimal("75000"))
                .build();
        
        when(payOrderUseCase.execute(orderId, 1L, null)).thenReturn(payment);
        
        // when
        PaymentResponse response = orderController.payOrder(orderId, 1L, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 주문 생성 시 예외 발생")
    void createOrder_UserNotFound() {
        // given
        Long userId = 999L;
        List<Long> productIds = List.of(1L);
        List<Long> couponIds = List.of();
        CreateOrderRequest request = new CreateOrderRequest(userId, productIds, couponIds);
        
        when(createOrderUseCase.execute(anyLong(), anyMap()))
                .thenThrow(new OrderException.InvalidUser());

        // when & then
        assertThatThrownBy(() -> orderController.createOrder(request))
                .isInstanceOf(OrderException.InvalidUser.class)
                .hasMessage("Invalid user ID");
    }

    @Test
    @DisplayName("빈 상품 리스트로 주문 생성 시 예외 발생")
    void createOrder_EmptyProductList() {
        // given
        Long userId = 1L;
        List<Long> productIds = Collections.emptyList();
        List<Long> couponIds = List.of();
        CreateOrderRequest request = new CreateOrderRequest(userId, productIds, couponIds);
        
        when(createOrderUseCase.execute(anyLong(), anyMap()))
                .thenThrow(new IllegalArgumentException("Order must contain at least one item"));

        // when & then
        assertThatThrownBy(() -> orderController.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order must contain at least one item");
    }

    @Test
    @DisplayName("재고 부족 상품으로 주문 생성 시 예외 발생")
    void createOrder_InsufficientStock() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(1L);
        List<Long> couponIds = List.of();
        CreateOrderRequest request = new CreateOrderRequest(userId, productIds, couponIds);
        
        when(createOrderUseCase.execute(anyLong(), anyMap()))
                .thenThrow(new ProductException.OutOfStock());

        // when & then
        assertThatThrownBy(() -> orderController.createOrder(request))
                .isInstanceOf(ProductException.OutOfStock.class)
                .hasMessage("Product out of stock");
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 예외 발생")
    void payOrder_OrderNotFound() {
        // given
        Long orderId = 999L;
        
        when(payOrderUseCase.execute(orderId, null, null))
                .thenThrow(new PaymentException.OrderNotFound());

        // when & then
        assertThatThrownBy(() -> orderController.payOrder(orderId, null, null))
                .isInstanceOf(PaymentException.OrderNotFound.class)
                .hasMessage("Order not found");
    }

    @Test
    @DisplayName("잔액 부족으로 결제 시 예외 발생")
    void payOrder_InsufficientBalance() {
        // given
        Long orderId = 1L;
        
        when(payOrderUseCase.execute(orderId, null, null))
                .thenThrow(new PaymentException.InsufficientBalance());

        // when & then
        assertThatThrownBy(() -> orderController.payOrder(orderId, null, null))
                .isInstanceOf(PaymentException.InsufficientBalance.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    @DisplayName("null 요청으로 주문 생성")
    void createOrder_WithNullRequest() {
        // when & then
        assertThatThrownBy(() -> orderController.createOrder(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 주문 ID로 결제")
    void payOrder_WithNullOrderId() {
        // when & then
        assertThatThrownBy(() -> orderController.payOrder(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderIds")
    @DisplayName("비정상 주문 ID로 결제")
    void payOrder_WithInvalidOrderIds(Long invalidOrderId) {
        // given
        when(payOrderUseCase.execute(invalidOrderId, null, null))
                .thenThrow(new PaymentException.OrderNotFound());

        // when & then
        assertThatThrownBy(() -> orderController.payOrder(invalidOrderId, null, null))
                .isInstanceOf(PaymentException.OrderNotFound.class);
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of(1L, List.of(1L), List.of(1L)), // 단일 상품, 단일 쿠폰
                Arguments.of(2L, List.of(1L, 2L), List.of()), // 다중 상품, 쿠폰 없음
                Arguments.of(3L, List.of(3L), List.of(1L, 2L)) // 단일 상품, 다중 쿠폰
        );
    }

    private static Stream<Arguments> provideOrderIds() {
        return Stream.of(
                Arguments.of(1L),
                Arguments.of(100L),
                Arguments.of(999L)
        );
    }

    private static Stream<Arguments> provideInvalidOrderIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE)
        );
    }
} 