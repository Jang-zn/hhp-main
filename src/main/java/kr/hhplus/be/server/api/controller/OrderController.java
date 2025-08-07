package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.response.OrderResponse;
import kr.hhplus.be.server.api.dto.response.PaymentResponse;
import kr.hhplus.be.server.api.docs.annotation.OrderApiDocs;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.facade.order.CreateOrderFacade;
import kr.hhplus.be.server.domain.facade.order.GetOrderFacade;
import kr.hhplus.be.server.domain.facade.order.GetOrderListFacade;
import kr.hhplus.be.server.domain.facade.order.GetOrderWithDetailsFacade;
import kr.hhplus.be.server.domain.facade.order.PayOrderFacade;
import kr.hhplus.be.server.domain.dto.OrderWithDetailsDto;

import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class OrderController {

    private final CreateOrderFacade createOrderFacade;
    private final PayOrderFacade payOrderFacade;
    private final GetOrderWithDetailsFacade getOrderWithDetailsFacade;

    @OrderApiDocs(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
  
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        
        
        // 상품 수량 정보를 타입 안전한 DTO로 변환
        List<ProductQuantityDto> productQuantities;
        
        if (request.getProducts() != null && !request.getProducts().isEmpty()) {
            // 새로운 products 필드 사용 (수량 정보 포함)
            productQuantities = request.getProducts().stream()
                    .map(p -> new ProductQuantityDto(p.getProductId(), p.getQuantity()))
                    .collect(Collectors.toList());
        } else if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            // 기존 productIds 필드 사용 (하위 호환성을 위해 수량 1로 설정)
            productQuantities = request.getProductIds().stream()
                    .map(productId -> new ProductQuantityDto(productId, 1))
                    .collect(Collectors.toList());
        } else {
            // 상품 정보가 없는 경우 예외 처리
            throw new OrderException.EmptyItems();
        }
        
        Order order = createOrderFacade.createOrder(request.getUserId(), productQuantities);
        
        // 파사드를 통해 상세 정보 조회
        OrderWithDetailsDto orderDetails = getOrderWithDetailsFacade.getOrderWithDetails(order.getId(), order.getUserId());
        
        return convertToOrderResponse(orderDetails);
    }

    @OrderApiDocs(summary = "주문 결제", description = "주문을 결제 처리합니다")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderRequest request) {
        
        
        Payment payment = payOrderFacade.payOrder(orderId, request.getUserId(), request.getCouponId());
        
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCreatedAt()  // paidAt 대신 createdAt 사용
        );
    }

    @OrderApiDocs(summary = "단일 주문 조회", description = "특정 주문의 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable @Positive Long orderId,
            @RequestParam @Positive Long userId) {
        
        
        // 파사드를 통해 상세 정보 조회
        OrderWithDetailsDto orderDetails = getOrderWithDetailsFacade.getOrderWithDetails(orderId, userId);
        
        return convertToOrderResponse(orderDetails);
    }

    @OrderApiDocs(summary = "사용자 주문 목록 조회", description = "사용자의 모든 주문 목록을 조회합니다")
    @GetMapping("/user/{userId}")
    public List<OrderResponse> getUserOrders(@PathVariable @Positive Long userId) {
        
        
        // 파사드를 통해 상세 정보 조회
        List<OrderWithDetailsDto> ordersWithDetails = getOrderWithDetailsFacade.getUserOrdersWithDetails(userId);
        
        return ordersWithDetails.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * OrderWithDetailsDto를 OrderResponse로 변환하는 helper 메소드
     */
    private OrderResponse convertToOrderResponse(OrderWithDetailsDto orderDetails) {
        List<OrderResponse.OrderItemResponse> itemResponses = orderDetails.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .collect(Collectors.toList());
        
        return new OrderResponse(
                orderDetails.getOrderId(),
                orderDetails.getUserId(),
                orderDetails.getStatus(),
                orderDetails.getTotalAmount(),
                orderDetails.getCreatedAt(),
                itemResponses
        );
    }
} 