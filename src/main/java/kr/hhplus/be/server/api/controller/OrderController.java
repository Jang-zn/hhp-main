package kr.hhplus.be.server.api.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
@Validated
public class OrderController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @NotNull(message = "사용자 ID는 필수입니다") @RequestParam Long userId,
            @NotEmpty(message = "상품 목록은 필수입니다") @RequestParam List<Long> productIds,
            @RequestParam(required = false) List<Long> couponIds) {
        // TODO: 주문 생성 로직 구현 (userId, productIds, couponIds)
        // Order order = createOrderUseCase.execute(userId, productIds, couponIds);
        return new OrderResponse(1L, userId, "PENDING", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now(),
                List.of(new OrderResponse.OrderItemResponse(1L, "노트북", 1, new java.math.BigDecimal("1200000"))));
    }

    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable Long orderId,
            @NotNull(message = "사용자 ID는 필수입니다") @RequestParam Long userId) {
        // TODO: 결제 처리 로직 구현 (orderId, userId)
        // Payment payment = payOrderUseCase.execute(orderId, userId);
        return new PaymentResponse(1L, orderId, "COMPLETED", 
                new java.math.BigDecimal("1200000"), 
                java.time.LocalDateTime.now());
    }
} 