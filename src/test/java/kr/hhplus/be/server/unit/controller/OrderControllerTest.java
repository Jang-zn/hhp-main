package kr.hhplus.be.server.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.facade.order.CreateOrderFacade;
import kr.hhplus.be.server.domain.facade.order.GetOrderWithDetailsFacade;
import kr.hhplus.be.server.domain.facade.order.PayOrderFacade;
import kr.hhplus.be.server.domain.dto.OrderWithDetailsDto;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController 비즈니스 시나리오 및 Bean Validation 테스트
 * 
 * Why: 주문 컨트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(OrderController.class)
@DisplayName("주문 컨트롤러 API 및 Validation 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateOrderFacade createOrderFacade;

    @MockBean
    private PayOrderFacade payOrderFacade;

    @MockBean
    private GetOrderWithDetailsFacade getOrderWithDetailsFacade;

    @Test
    @DisplayName("고객이 상품을 성공적으로 주문한다")
    void createOrder_Success() throws Exception {
        // given - 고객이 상품을 주문하는 상황
        Long customerId = 1L;
        List<Long> productIds = List.of(1L, 2L);
        List<Long> couponIds = List.of();
        OrderRequest request = new OrderRequest(customerId, productIds, couponIds);

        Order createdOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(1L)
                .userId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("20000"))
                .build();

        when(createOrderFacade.createOrder(eq(customerId), anyList()))
                .thenReturn(createdOrder);
        
        // Mock for getOrderWithDetails call in controller
        OrderWithDetailsDto orderDetails = new OrderWithDetailsDto(
                createdOrder.getId(), customerId, "PENDING", createdOrder.getTotalAmount(), 
                LocalDateTime.now(), List.of()
        );
        when(getOrderWithDetailsFacade.getOrderWithDetails(createdOrder.getId(), customerId))
                .thenReturn(orderDetails);

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    static Stream<Arguments> provideInvalidOrderRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, List.of(1L), List.of(), "사용자 ID가 null"),
                // userId가 음수
                Arguments.of(-1L, List.of(1L), List.of(), "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, List.of(1L), List.of(), "사용자 ID가 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderRequests")
    @DisplayName("유효하지 않은 주문 요청 시 Bean Validation 에러가 발생한다")
    void createOrder_InvalidRequest_ValidationError(Long userId, List<Long> productIds, List<Long> couponIds, String description) throws Exception {
        // given
        OrderRequest request = new OrderRequest(userId, productIds, couponIds);

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 요청 본문으로 주문 생성 시 validation 에러가 발생한다")
    void createOrder_EmptyBody_ValidationError() throws Exception {
        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 상품 주문 시 예외가 발생한다")
    void createOrder_ProductNotFound() throws Exception {
        // given - 존재하지 않는 상품 주문 시도
        Long customerId = 1L;
        List<Long> productIds = List.of(999L); // 존재하지 않는 상품
        List<Long> couponIds = List.of();
        OrderRequest request = new OrderRequest(customerId, productIds, couponIds);

        when(createOrderFacade.createOrder(eq(customerId), anyList()))
                .thenThrow(new ProductException.NotFound());

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("고객이 주문을 성공적으로 결제한다")
    void payOrder_Success() throws Exception {
        // given - 고객이 주문을 결제하는 상황
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = null;

        Payment payment = TestBuilder.PaymentBuilder.defaultPayment()
                .id(1L)
                .orderId(orderId)
                .status(PaymentStatus.PAID)
                .amount(new BigDecimal("20000"))
                .build();

        when(payOrderFacade.payOrder(orderId, userId, couponId)).thenReturn(payment);

        // when & then - RequestBody로 userId와 couponId를 전달
        OrderRequest payRequest = new OrderRequest(userId, couponId);
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    static Stream<Arguments> provideInvalidOrderIds() {
        return Stream.of(
                Arguments.of(-1L, "음수 주문 ID"),
                Arguments.of(0L, "0인 주문 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderIds")
    @DisplayName("유효하지 않은 주문 ID로 결제 시 validation 에러가 발생한다")
    void payOrder_InvalidOrderId_ValidationError(Long invalidOrderId, String description) throws Exception {
        // given
        OrderRequest payRequest = new OrderRequest(1L, null);
        
        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", invalidOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 예외가 발생한다")
    void payOrder_OrderNotFound() throws Exception {
        // given - 존재하지 않는 주문 결제 시도
        Long invalidOrderId = 999L;
        Long userId = 1L;
        Long couponId = null;
        OrderRequest payRequest = new OrderRequest(userId, couponId);

        when(payOrderFacade.payOrder(invalidOrderId, userId, couponId))
                .thenThrow(new OrderException.NotFound());

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", invalidOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("O001"));
    }

    @Test
    @DisplayName("고객이 주문 상세 정보를 성공적으로 조회한다")
    void getOrderWithDetails_Success() throws Exception {
        // given - 고객이 주문 상세를 조회하는 상황
        Long orderId = 1L;
        Long userId = 1L;

        // OrderWithDetailsDto를 올바른 생성자로 생성
        OrderWithDetailsDto orderDetails = new OrderWithDetailsDto(
                orderId, userId, "PAID", new BigDecimal("20000"), 
                LocalDateTime.now(), List.of()
        );

        when(getOrderWithDetailsFacade.getOrderWithDetails(orderId, userId))
                .thenReturn(orderDetails);

        // when & then - userId를 쿼리 파라미터로 전달
        mockMvc.perform(get("/api/order/{orderId}", orderId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderIds")
    @DisplayName("유효하지 않은 주문 ID로 조회 시 validation 에러가 발생한다")
    void getOrderWithDetails_InvalidOrderId_ValidationError(Long invalidOrderId, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/order/{orderId}", invalidOrderId)
                .param("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }
}