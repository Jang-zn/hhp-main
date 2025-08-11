package kr.hhplus.be.server.unit.controller.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.exception.*;
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
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController.payOrder 메서드 테스트
 * 
 * Why: 주문 결제 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(OrderController.class)
@DisplayName("주문 결제 컨트롤러 API")
class PayOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("고객이 주문을 성공적으로 결제한다")
    void payOrder_Success() throws Exception {
        // given - 고객이 주문을 결제하는 상황
        Long orderId = 1L;
        Long customerId = 1L;
        Long couponId = 1L;
        OrderRequest request = new OrderRequest(customerId, couponId);

        Payment completedPayment = TestBuilder.PaymentBuilder.defaultPayment()
                .id(1L)
                .orderId(orderId)
                .userId(customerId)
                .amount(new BigDecimal("67500")) // 쿠폰 적용 후 금액
                .build();

        when(orderService.payOrder(orderId, customerId, couponId)).thenReturn(completedPayment);

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.paymentId").value(1L))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.finalAmount").value(67500));
    }

    @Test
    @DisplayName("쿠폰 없이 주문을 성공적으로 결제한다")
    void payOrder_WithoutCoupon_Success() throws Exception {
        // given
        Long orderId = 1L;
        Long customerId = 1L;
        OrderRequest request = new OrderRequest(customerId, null);

        Payment completedPayment = TestBuilder.PaymentBuilder.defaultPayment()
                .id(1L)
                .orderId(orderId)
                .userId(customerId)
                .amount(new BigDecimal("75000"))
                .build();

        when(orderService.payOrder(orderId, customerId, null)).thenReturn(completedPayment);

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.finalAmount").value(75000));
    }

    static Stream<Arguments> provideInvalidOrderRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, 1L, "사용자 ID가 null"),
                // userId가 음수
                Arguments.of(-1L, 1L, "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, 1L, "사용자 ID가 0"),
                // couponId가 음수
                Arguments.of(1L, -1L, "쿠폰 ID가 음수"),
                // couponId가 0
                Arguments.of(1L, 0L, "쿠폰 ID가 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderRequests")
    @DisplayName("유효하지 않은 주문 결제 요청 시 Bean Validation 에러가 발생한다")
    void payOrder_InvalidRequest_ValidationError(Long userId, Long couponId, String description) throws Exception {
        // given
        Long orderId = 1L;
        OrderRequest request = new OrderRequest(userId, couponId);

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
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
        Long customerId = 1L;
        OrderRequest request = new OrderRequest(customerId, null);

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", invalidOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 주문으로 결제 시 예외가 발생한다")
    void payOrder_OrderNotFound() throws Exception {
        // given
        Long invalidOrderId = 999L;
        Long customerId = 1L;
        OrderRequest request = new OrderRequest(customerId, null);

        when(orderService.payOrder(invalidOrderId, customerId, null))
                .thenThrow(new OrderException.NotFound());

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", invalidOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("O001"));
    }

    @Test
    @DisplayName("잔액 부족으로 결제 실패 시 예외가 발생한다")
    void payOrder_InsufficientBalance() throws Exception {
        // given
        Long orderId = 1L;
        Long customerId = 1L;
        OrderRequest request = new OrderRequest(customerId, null);

        when(orderService.payOrder(orderId, customerId, null))
                .thenThrow(new BalanceException.InsufficientBalance());

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("B002"));
    }

    @Test
    @DisplayName("이미 결제된 주문으로 재결제 시 예외가 발생한다")
    void payOrder_AlreadyPaid() throws Exception {
        // given
        Long orderId = 1L;
        Long customerId = 1L;
        OrderRequest request = new OrderRequest(customerId, null);

        when(orderService.payOrder(orderId, customerId, null))
                .thenThrow(new OrderException.AlreadyPaid());

        // when & then
        mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("O003"));
    }
}