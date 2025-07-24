package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.docs.annotation.OrderApiDocs;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.usecase.order.*;
import kr.hhplus.be.server.domain.usecase.coupon.ValidateCouponUseCase;

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
    private final GetOrderUseCase getOrderUseCase;
    private final GetOrderListUseCase getOrderListUseCase;
    private final CheckOrderAccessUseCase checkOrderAccessUseCase;
    private final ValidateCouponUseCase validateCouponUseCase;

    @OrderApiDocs(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
  
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        request.validate();
        
        // 상품 수량 정보를 Map<Long, Integer> 형태로 변환
        Map<Long, Integer> productQuantities;
        
        if (request.getProducts() != null && !request.getProducts().isEmpty()) {
            // 새로운 products 필드 사용 (수량 정보 포함)
            productQuantities = request.getProducts().stream()
                    .collect(Collectors.toMap(
                            OrderRequest.ProductQuantity::getProductId,
                            OrderRequest.ProductQuantity::getQuantity
                    ));
        } else if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            // 기존 productIds 필드 사용 (하위 호환성을 위해 수량 1로 설정)
            productQuantities = request.getProductIds().stream()
                    .collect(Collectors.toMap(
                            productId -> productId,
                            productId -> 1
                    ));
        } else {
            // 상품 정보가 없는 경우 예외 처리
            throw new OrderException.EmptyItems();
        }
        
        // 쿠폰 유효성 검증 (있는 경우에만)
        validateCouponUseCase.execute(request.getCouponIds());
        
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
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    @OrderApiDocs(summary = "주문 결제", description = "주문을 결제 처리합니다")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRequest request) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        request.validate();
        
        Payment payment = payOrderUseCase.execute(orderId, request.getUserId(), request.getCouponId());
        
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCreatedAt()  // paidAt 대신 createdAt 사용
        );
    }

    @OrderApiDocs(summary = "단일 주문 조회", description = "특정 주문의 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable Long orderId,
            @RequestParam Long userId) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        if (userId == null) {
            throw new CommonException.InvalidRequest();
        }
        
        // CheckOrderAccessUseCase를 사용해서 권한과 존재 여부를 적절히 구분
        Order order = checkOrderAccessUseCase.execute(userId, orderId);
        
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
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    @OrderApiDocs(summary = "사용자 주문 목록 조회", description = "사용자의 모든 주문 목록을 조회합니다")
    @GetMapping("/user/{userId}")
    public List<OrderResponse> getUserOrders(@PathVariable Long userId) {
        if (userId == null) {
            throw new CommonException.InvalidRequest();
        }
        
        List<Order> orders = getOrderListUseCase.execute(userId);
        
        return orders.stream()
                .map(order -> {
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
                            order.getStatus().name(),
                            order.getTotalAmount(),
                            order.getCreatedAt(),
                            itemResponses
                    );
                })
                .collect(Collectors.toList());
    }
} 