package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.docs.annotation.OrderApiDocs;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;

import kr.hhplus.be.server.domain.service.OrderService;

import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 주문/결제 관리 Controller
 * 주문 생성 및 결제 처리 기능을 제공합니다.
 */
@Tag(name = "주문/결제 관리")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    @OrderApiDocs(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        List<ProductQuantityDto> productQuantities;
        
        if (request.getProducts() != null && !request.getProducts().isEmpty()) {
            // 새로운 products 필드 사용 (수량 정보 포함)
            productQuantities = request.getProducts().stream()
                    .map(p -> new ProductQuantityDto(p.getProductId(), p.getQuantity()))
                    .toList();
        } else if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            // 기존 productIds 필드 사용 (하위 호환성을 위해 수량 1로 설정)
            productQuantities = request.getProductIds().stream()
                    .map(productId -> new ProductQuantityDto(productId, 1))
                    .toList();
        } else {
            // Bean Validation으로 이미 검증되었으므로 여기까지 오면 안됨
            productQuantities = List.of();
        }
        
        Order order = orderService.createOrder(request.getUserId(), productQuantities);
        
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                List.of()
        );
    }

    @OrderApiDocs(summary = "주문 결제", description = "주문을 결제 처리합니다")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderRequest request) {
        Payment payment = orderService.payOrder(orderId, request.getUserId(), request.getCouponId());
        
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCreatedAt()
        );
    }

    @OrderApiDocs(summary = "단일 주문 조회", description = "특정 주문의 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable @Positive Long orderId,
            @RequestParam @Positive Long userId) {
        Order orderDetails = orderService.getOrderWithDetails(orderId, userId);
        
        return new OrderResponse(
                orderDetails.getId(),
                orderDetails.getUserId(),
                orderDetails.getStatus().name(),
                orderDetails.getTotalAmount(),
                orderDetails.getCreatedAt(),
                List.of()
        );
    }

    @OrderApiDocs(summary = "사용자 주문 목록 조회", description = "사용자의 모든 주문 목록을 조회합니다")
    @GetMapping("/user/{userId}")
    public List<OrderResponse> getUserOrders(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset) {
        List<Order> orders = orderService.getOrderList(userId, limit, offset);
        
        return orders.stream()
                .map(order -> new OrderResponse(
                        order.getId(),
                        order.getUserId(),
                        order.getStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        List.of()
                ))
                .toList();
    }
    
} 