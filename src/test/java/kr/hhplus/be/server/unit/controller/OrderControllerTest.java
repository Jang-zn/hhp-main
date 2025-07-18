package kr.hhplus.be.server.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.api.dto.request.CreateOrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderController 단위 테스트")
class OrderControllerTest {

    private OrderController orderController;
    private CreateOrderUseCase createOrderUseCase;
    private PayOrderUseCase payOrderUseCase;

    @BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(null, null, null, null, null, null);
        payOrderUseCase = new PayOrderUseCase(null, null, null, null, null, null, null, null, null);
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

        // when
        PaymentResponse response = orderController.payOrder(orderId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("provideOrderIds")
    @DisplayName("다양한 주문 ID로 결제")
    void payOrder_WithDifferentOrderIds(Long orderId) {
        // when
        PaymentResponse response = orderController.payOrder(orderId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
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
} 