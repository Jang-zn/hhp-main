package kr.hhplus.be.server.unit.controller.order;

import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import kr.hhplus.be.server.util.ControllerTestBase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController.getOrder 메서드 테스트
 */
@Transactional
@DisplayName("주문 조회 컨트롤러 API")
class GetOrderControllerTest extends ControllerTestBase {

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("고객이 자신의 주문을 성공적으로 조회한다")
    void getOrder_Success() throws Exception {
        // given
        Long orderId = 1L;
        Long customerId = 1L;
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(customerId)
                .totalAmount(new BigDecimal("75000"))
                .build();

        when(orderService.getOrderWithDetails(orderId, customerId)).thenReturn(order);

        // when & then
        mockMvc.perform(get("/api/order/{orderId}", orderId)
                .param("userId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.totalAmount").value(75000));
    }

    static Stream<Arguments> provideInvalidOrderIds() {
        return Stream.of(
                Arguments.of(-1L, "음수 주문 ID"),
                Arguments.of(0L, "0인 주문 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderIds")
    @DisplayName("유효하지 않은 주문 ID로 조회 시 validation 에러가 발생한다")
    void getOrder_InvalidOrderId_ValidationError(Long invalidOrderId, String description) throws Exception {
        // given
        Long customerId = 1L;

        // when & then
        mockMvc.perform(get("/api/order/{orderId}", invalidOrderId)
                .param("userId", customerId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 예외가 발생한다")
    void getOrder_OrderNotFound() throws Exception {
        // given
        Long invalidOrderId = 999L;
        Long customerId = 1L;

        when(orderService.getOrderWithDetails(invalidOrderId, customerId))
                .thenThrow(new OrderException.NotFound());

        // when & then
        mockMvc.perform(get("/api/order/{orderId}", invalidOrderId)
                .param("userId", customerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("O001"));
    }

    @Test
    @DisplayName("다른 사용자의 주문 조회 시 권한 없음 예외가 발생한다")
    void getOrder_Unauthorized() throws Exception {
        // given
        Long orderId = 1L;
        Long unauthorizedUserId = 2L;

        when(orderService.getOrderWithDetails(orderId, unauthorizedUserId))
                .thenThrow(new OrderException.Unauthorized());

        // when & then
        mockMvc.perform(get("/api/order/{orderId}", orderId)
                .param("userId", unauthorizedUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("U403"));
    }
}