package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.CreateOrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.swagger.ApiCreate;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문/결제 관리 Controller
 * 주문 생성 및 결제 처리 기능을 제공합니다.
 */
@Tag(name = "주문/결제 관리", description = "주문 생성 및 결제 처리 API")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @ApiCreate(summary = "주문 생성", description = "새로운 주문을 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // TODO: 주문 생성 로직 구현 (request)
        // Order order = createOrderUseCase.execute(request);
        return new OrderResponse(1L, request.getUserId(), "PENDING", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now(),
                List.of(new OrderResponse.OrderItemResponse(1L, "노트북", 1, new java.math.BigDecimal("1200000"))));
    }

    @ApiSuccess(summary = "주문 결제", description = "주문에 대한 결제를 처리합니다.")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(@PathVariable Long orderId) {
        // TODO: 결제 처리 로직 구현 (orderId)
        // Payment payment = payOrderUseCase.execute(orderId);
        return new PaymentResponse(1L, orderId, "COMPLETED", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now());
    }
} 