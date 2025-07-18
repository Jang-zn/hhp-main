package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.swagger.ApiCreate;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.usecase.order.*;
import org.springframework.validation.annotation.Validated;


import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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

    @ApiCreate(summary = "주문 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
  
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        // productIds를 Map<Long, Integer> 형태로 변환 (각 상품의 수량을 1로 설정)
        Map<Long, Integer> productQuantities = request.getProductIds().stream()
                .collect(Collectors.toMap(
                        productId -> productId,
                        productId -> 1  // 기본 수량 1
                ));
        
        Order order = createOrderUseCase.execute(request.getUserId(), productQuantities);
        
        // OrderItem들을 OrderItemResponse로 변환
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getProduct().getPrice()
                ))
                .collect(Collectors.toList());
        
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                "PENDING",  // Order 엔티티에 status 필드가 없으므로 기본값 사용
                order.getTotalAmount(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    @ApiSuccess(summary = "주문 결제")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRequest request) {
        Payment payment = payOrderUseCase.execute(orderId, request.getUserId(), request.getCouponId());
        
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCreatedAt()  // paidAt 대신 createdAt 사용
        );
    }
} 