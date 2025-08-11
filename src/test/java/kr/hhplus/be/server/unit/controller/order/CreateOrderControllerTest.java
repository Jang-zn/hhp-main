package kr.hhplus.be.server.unit.controller.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.request.OrderRequest.ProductQuantity;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
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
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController.createOrder 메서드 테스트
 * 
 * Why: 주문 생성 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(OrderController.class)
@DisplayName("주문 생성 컨트롤러 API")
class CreateOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("고객이 상품을 성공적으로 주문한다")
    void createOrder_Success() throws Exception {
        // given - 고객이 장바구니에서 주문하는 상황
        Long customerId = 1L;
        List<ProductQuantity> products = List.of(
                new ProductQuantity(1L, 2),
                new ProductQuantity(2L, 1)
        );
        OrderRequest request = new OrderRequest();
        request.setUserId(customerId);
        request.setProducts(products);

        Order createdOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(1L)
                .userId(customerId)
                .totalAmount(new BigDecimal("75000"))
                .build();

        when(orderService.createOrder(eq(customerId), any(List.class))).thenReturn(createdOrder);

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.orderId").value(1L))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.totalAmount").value(75000));
    }

    static Stream<Arguments> provideInvalidOrderRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, List.of(new ProductQuantity(1L, 2)), "사용자 ID가 null"),
                // userId가 음수
                Arguments.of(-1L, List.of(new ProductQuantity(1L, 2)), "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, List.of(new ProductQuantity(1L, 2)), "사용자 ID가 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOrderRequests")
    @DisplayName("유효하지 않은 주문 생성 요청 시 Bean Validation 에러가 발생한다")
    void createOrder_InvalidRequest_ValidationError(Long userId, List<ProductQuantity> products, String description) throws Exception {
        // given
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setProducts(products);

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
    @DisplayName("null 상품 목록으로 주문 생성 시 validation 에러가 발생한다")
    void createOrder_NullProducts_ValidationError() throws Exception {
        // given
        OrderRequest request = new OrderRequest();
        request.setUserId(1L);
        request.setProducts(null);

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 상품 목록으로 주문 생성 시 validation 에러가 발생한다")
    void createOrder_EmptyProducts_ValidationError() throws Exception {
        // given
        OrderRequest request = new OrderRequest();
        request.setUserId(1L);
        request.setProducts(List.of());

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 주문 생성 시 예외가 발생한다")
    void createOrder_UserNotFound() throws Exception {
        // given
        Long invalidUserId = 999L;
        List<ProductQuantity> products = List.of(
                new ProductQuantity(1L, 2)
        );
        OrderRequest request = new OrderRequest();
        request.setUserId(invalidUserId);
        request.setProducts(products);

        when(orderService.createOrder(eq(invalidUserId), any(List.class)))
                .thenThrow(new UserException.NotFound());

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("재고 부족 상품으로 주문 생성 시 예외가 발생한다")
    void createOrder_ProductOutOfStock() throws Exception {
        // given
        Long customerId = 1L;
        List<ProductQuantity> products = List.of(
                new ProductQuantity(1L, 999) // 재고 부족
        );
        OrderRequest request = new OrderRequest();
        request.setUserId(customerId);
        request.setProducts(products);

        when(orderService.createOrder(eq(customerId), any(List.class)))
                .thenThrow(new ProductException.OutOfStock());

        // when & then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));
    }
}