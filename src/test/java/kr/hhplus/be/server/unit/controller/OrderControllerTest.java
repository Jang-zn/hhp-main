package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.TestConstants;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.facade.order.CreateOrderFacade;
import kr.hhplus.be.server.domain.facade.order.GetOrderWithDetailsFacade;
import kr.hhplus.be.server.domain.facade.order.PayOrderFacade;
import kr.hhplus.be.server.domain.dto.OrderWithDetailsDto;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderController 비즈니스 시나리오 테스트
 * 
 * Why: 주문 컴트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하는지 검증
 * How: 주문 및 결제 시나리오를 반영한 컴트롤러 레이어 테스트로 구성
 */
@DisplayName("주문 컴트롤러 API 비즈니스 시나리오")
class OrderControllerTest {

    @Mock
    private CreateOrderFacade createOrderFacade;
    @Mock
    private PayOrderFacade payOrderFacade;
    @Mock
    private GetOrderWithDetailsFacade getOrderWithDetailsFacade;
    
    private OrderController orderController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderController = new OrderController(
            createOrderFacade,
            payOrderFacade,
            getOrderWithDetailsFacade
        );
    }

    @Test
    @DisplayName("고객이 상품을 주문하여 성공적으로 주문을 생성한다")
    void createOrder_Success() {
        // given - 고객이 상품을 선택하여 주문을 생성하는 상황
        OrderRequest orderRequest = new OrderRequest(1L, List.of(1L), List.of());
        Order createdOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .userId(1L)
                .totalAmount(new BigDecimal("100000"))
                .build();
        OrderWithDetailsDto orderDetails = new OrderWithDetailsDto(
            1L, 1L, "PENDING", new BigDecimal("100000"), LocalDateTime.now(), List.of()
        );
        
        when(createOrderFacade.createOrder(eq(1L), anyList())).thenReturn(createdOrder);
        when(getOrderWithDetailsFacade.getOrderWithDetails(eq(1L), eq(1L))).thenReturn(orderDetails);
        
        // when
        OrderResponse result = orderController.createOrder(orderRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("100000"));
        verify(createOrderFacade).createOrder(eq(1L), anyList());
        verify(getOrderWithDetailsFacade).getOrderWithDetails(eq(1L), eq(1L));
    }
    
    @Test
    @DisplayName("잘못된 요청 형식으로 주문 생성 시 예외가 발생한다")
    void createOrder_NullRequest() {
        // given - 잘못된 API 요청
        OrderRequest invalidRequest = null;
        
        // when & then
        assertThatThrownBy(() -> orderController.createOrder(invalidRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
        verify(createOrderFacade, never()).createOrder(anyLong(), anyList());
    }
    
    @Test
    @DisplayName("고객이 주문에 대해 결제를 성공적으로 완료한다")
    void payOrder_Success() {
        // given - 고객이 대기 중인 주문에 대해 결제를 진행하는 상황
        Long orderId = 1L;
        OrderRequest paymentRequest = new OrderRequest(1L, 1L);
        Payment completedPayment = TestBuilder.PaymentBuilder.paidPayment()
                .id(1L)
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(payOrderFacade.payOrder(orderId, 1L, 1L)).thenReturn(completedPayment);
        
        // when
        PaymentResponse result = orderController.payOrder(orderId, paymentRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(result.finalAmount()).isEqualTo(new BigDecimal("100000"));
        verify(payOrderFacade).payOrder(orderId, 1L, 1L);
    }
    
    @Test
    @DisplayName("잘못된 주문 ID로 결제 시도 시 예외가 발생한다")
    void payOrder_NullOrderId() {
        // given - 잘못된 주문 ID로 결제 요청
        Long nullOrderId = null;
        OrderRequest paymentRequest = new OrderRequest(1L, 1L);
        
        // when & then
        assertThatThrownBy(() -> orderController.payOrder(nullOrderId, paymentRequest))
            .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
        verify(payOrderFacade, never()).payOrder(anyLong(), anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("고객이 자신의 주문 상세 정보를 성공적으로 조회한다")
    void getOrder_Success() {
        // given - 고객이 자신의 주문 내역을 확인하는 상황
        Long orderId = 1L;
        Long customerId = 1L;
        OrderWithDetailsDto customerOrderDetails = new OrderWithDetailsDto(
            1L, 1L, "PENDING", new BigDecimal("100000"), LocalDateTime.now(), List.of()
        );
        
        when(getOrderWithDetailsFacade.getOrderWithDetails(orderId, customerId)).thenReturn(customerOrderDetails);
        
        // when
        OrderResponse result = orderController.getOrder(orderId, customerId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("100000"));
        verify(getOrderWithDetailsFacade).getOrderWithDetails(orderId, customerId);
    }
    
    @Test
    @DisplayName("잘못된 주문 ID로 조회 시 예외가 발생한다")
    void getOrder_NullOrderId() {
        // given - 잘못된 주문 ID로 조회 요청
        Long nullOrderId = null;
        Long customerId = 1L;
        
        // when & then
        assertThatThrownBy(() -> orderController.getOrder(nullOrderId, customerId))
            .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
        verify(getOrderWithDetailsFacade, never()).getOrderWithDetails(anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("고객이 자신의 전체 주문 목록을 성공적으로 조회한다")
    void getUserOrders_Success() {
        // given - 고객이 자신의 모든 주문 내역을 확인하는 상황
        Long customerId = 1L;
        OrderWithDetailsDto customerOrderHistory = new OrderWithDetailsDto(
            1L, 1L, "PENDING", new BigDecimal("100000"), LocalDateTime.now(), List.of()
        );
        when(getOrderWithDetailsFacade.getUserOrdersWithDetails(customerId)).thenReturn(List.of(customerOrderHistory));
        
        // when
        List<OrderResponse> result = orderController.getUserOrders(customerId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo(1L);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(result.get(0).totalAmount()).isEqualTo(new BigDecimal("100000"));
        verify(getOrderWithDetailsFacade).getUserOrdersWithDetails(customerId);
    }
    
    @Test
    @DisplayName("잘못된 사용자 ID로 주문 목록 조회 시 예외가 발생한다")
    void getUserOrders_NullUserId() {
        // given - 잘못된 사용자 ID로 주문 목록 조회 요청
        Long nullUserId = null;
        
        // when & then
        assertThatThrownBy(() -> orderController.getUserOrders(nullUserId))
            .isInstanceOf(CommonException.InvalidRequest.class);
        verify(getOrderWithDetailsFacade, never()).getUserOrdersWithDetails(anyLong());
    }
}