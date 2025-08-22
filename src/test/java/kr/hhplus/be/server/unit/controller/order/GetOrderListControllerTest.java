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
import kr.hhplus.be.server.api.controller.OrderController;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController.getOrderList 메서드 테스트
 */
@WebMvcTest(OrderController.class)
@ActiveProfiles("unit")
@DisplayName("주문 목록 조회 컨트롤러 API")
class GetOrderListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("고객이 자신의 주문 목록을 성공적으로 조회한다")
    void getOrderList_Success() throws Exception {
        // given
        Long customerId = 1L;
        int limit = 10;
        int offset = 0;

        List<Order> orders = List.of(
                TestBuilder.OrderBuilder.defaultOrder()
                        .id(1L)
                        .userId(customerId)
                        .totalAmount(new BigDecimal("75000"))
                        .build(),
                TestBuilder.OrderBuilder.defaultOrder()
                        .id(2L)
                        .userId(customerId)
                        .totalAmount(new BigDecimal("50000"))
                        .build()
        );

        when(orderService.getOrderList(customerId, limit, offset)).thenReturn(orders);

        // when & then
        mockMvc.perform(get("/api/order/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderId").value(1L))
                .andExpect(jsonPath("$.data[0].userId").value(customerId))
                .andExpect(jsonPath("$.data[1].orderId").value(2L))
                .andExpect(jsonPath("$.data[1].userId").value(customerId));
    }

    @Test
    @DisplayName("빈 주문 목록 조회가 성공한다")
    void getOrderList_EmptyList() throws Exception {
        // given
        Long customerId = 1L;
        int limit = 10;
        int offset = 0;

        when(orderService.getOrderList(customerId, limit, offset)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/order/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L, "음수 사용자 ID"),
                Arguments.of(0L, "0인 사용자 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("유효하지 않은 사용자 ID로 주문 목록 조회 시 validation 에러가 발생한다")
    void getOrderList_InvalidUserId_ValidationError(Long invalidUserId, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/order/user/{userId}", invalidUserId)
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                Arguments.of(-1, 0, "음수 limit"),
                Arguments.of(0, 0, "0인 limit"),
                Arguments.of(101, 0, "최대값 초과 limit"),
                Arguments.of(10, -1, "음수 offset")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("유효하지 않은 페이징 파라미터로 조회 시 validation 에러가 발생한다")
    void getOrderList_InvalidPaginationParams_ValidationError(int limit, int offset, String description) throws Exception {
        // given
        Long customerId = 1L;

        // when & then
        mockMvc.perform(get("/api/order/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 주문 목록 조회 시 예외가 발생한다")
    void getOrderList_UserNotFound() throws Exception {
        // given
        Long invalidUserId = 999L;
        int limit = 10;
        int offset = 0;

        when(orderService.getOrderList(invalidUserId, limit, offset))
                .thenThrow(new UserException.NotFound());

        // when & then
        mockMvc.perform(get("/api/order/user/{userId}", invalidUserId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }
}