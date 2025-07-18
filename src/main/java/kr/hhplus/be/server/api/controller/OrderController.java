package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.CreateOrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.swagger.ApiCreate;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문/결제 관리 Controller
 * 주문 생성 및 결제 처리 기능을 제공합니다.
 */
@Tag(name = "주문/결제 관리")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;

    /**
     * Creates a new order based on the provided request data.
     *
     * @param request the order creation details
     * @return the created order information
     */
    @ApiCreate(summary = "주문 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // TODO: 주문 생성 로직 구현
        // Order order = createOrderUseCase.execute(request.getUserId(), request.getProductIds());
        return new OrderResponse(1L, request.getUserId(), "PENDING", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now(),
                List.of(new OrderResponse.OrderItemResponse(1L, "노트북", 1, new java.math.BigDecimal("1200000"))));
    }

    /**
     * Processes payment for the specified order and returns the payment result.
     *
     * @param orderId the ID of the order to be paid
     * @return a response containing payment details for the order
     */
    @ApiSuccess(summary = "주문 결제")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(@PathVariable Long orderId) {
        // TODO: 결제 처리 로직 구현
        // Payment payment = payOrderUseCase.execute(orderId, userId, couponId);
        return new PaymentResponse(1L, orderId, "COMPLETED", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now());
    }
} 