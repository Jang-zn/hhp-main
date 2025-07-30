package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.TestConstants;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.usecase.order.*;
import kr.hhplus.be.server.domain.usecase.coupon.ValidateCouponUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OrderController 단위 테스트")
class OrderControllerTest {

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
    
    private OrderController orderController;
    
    private User testUser;
    private Order testOrder;
    private Payment testPayment;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderController = new OrderController(
            createOrderUseCase,
            payOrderUseCase,
            getOrderUseCase,
            getOrderListUseCase,
            checkOrderAccessUseCase,
            validateCouponUseCase
        );
        
        testUser = User.builder()
            .id(1L)
            .name(TestConstants.TEST_USER_NAME)
            .build();
            
        testOrder = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.PENDING)
            .totalAmount(TestConstants.DEFAULT_ORDER_AMOUNT)
            .createdAt(LocalDateTime.now())
            .build();
            
        testPayment = Payment.builder()
            .id(1L)
            .order(testOrder)
            .status(PaymentStatus.PAID)
            .amount(TestConstants.DEFAULT_ORDER_AMOUNT)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("성공 - 정상 주문 생성")
    void createOrder_Success() {
        // given
        OrderRequest request = new OrderRequest(1L, List.of(1L), List.of());
        
        doNothing().when(validateCouponUseCase).execute(anyList());
        when(createOrderUseCase.execute(eq(1L), any(Map.class))).thenReturn(testOrder);
        
        // when
        OrderResponse result = orderController.createOrder(request);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("100000"));
        
        verify(validateCouponUseCase).execute(anyList());
        verify(createOrderUseCase).execute(eq(1L), any(Map.class));
    }
    
    @Test
    @DisplayName("실패 - null 요청")
    void createOrder_NullRequest() {
        // given
        OrderRequest nullRequest = null;
        
        // when & then
        assertThatThrownBy(() -> orderController.createOrder(nullRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
            
        verify(validateCouponUseCase, never()).execute(anyList());
        verify(createOrderUseCase, never()).execute(anyLong(), any(Map.class));
    }
    
    @Test
    @DisplayName("성공 - 정상 결제 처리")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        OrderRequest request = new OrderRequest(1L, 1L);
        
        when(payOrderUseCase.execute(orderId, 1L, 1L)).thenReturn(testPayment);
        
        // when
        PaymentResponse result = orderController.payOrder(orderId, request);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(result.finalAmount()).isEqualTo(new BigDecimal("100000"));
        
        verify(payOrderUseCase).execute(orderId, 1L, 1L);
    }
    
    @Test
    @DisplayName("실패 - null 주문 ID")
    void payOrder_NullOrderId() {
        // given
        Long nullOrderId = null;
        OrderRequest request = new OrderRequest(1L, 1L);
        
        // when & then
        assertThatThrownBy(() -> orderController.payOrder(nullOrderId, request))
            .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
            
        verify(payOrderUseCase, never()).execute(anyLong(), anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("성공 - 정상 주문 조회")
    void getOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        
        when(checkOrderAccessUseCase.execute(userId, orderId)).thenReturn(testOrder);
        
        // when
        OrderResponse result = orderController.getOrder(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("100000"));
        
        verify(checkOrderAccessUseCase).execute(userId, orderId);
    }
    
    @Test
    @DisplayName("실패 - null 주문 ID")
    void getOrder_NullOrderId() {
        // given
        Long nullOrderId = null;
        Long userId = 1L;
        
        // when & then
        assertThatThrownBy(() -> orderController.getOrder(nullOrderId, userId))
            .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
            
        verify(checkOrderAccessUseCase, never()).execute(anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("성공 - 정상 주문 목록 조회")
    void getUserOrders_Success() {
        // given
        Long userId = 1L;
        when(getOrderListUseCase.execute(userId)).thenReturn(List.of(testOrder));
        
        // when
        List<OrderResponse> result = orderController.getUserOrders(userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo(1L);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.get(0).totalAmount()).isEqualTo(new BigDecimal("100000"));
        
        verify(getOrderListUseCase).execute(userId);
    }
    
    @Test
    @DisplayName("실패 - null 사용자 ID")
    void getUserOrders_NullUserId() {
        // given
        Long nullUserId = null;
        
        // when & then
        assertThatThrownBy(() -> orderController.getUserOrders(nullUserId))
            .isInstanceOf(CommonException.InvalidRequest.class);
            
        verify(getOrderListUseCase, never()).execute(anyLong());
    }
}