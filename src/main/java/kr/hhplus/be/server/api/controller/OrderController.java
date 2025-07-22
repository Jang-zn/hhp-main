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
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.usecase.order.*;

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
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        
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
            productQuantities = new HashMap<>();
        }
        
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

    @ApiSuccess(summary = "주문 결제")
    @PostMapping("/{orderId}/pay")
    public PaymentResponse payOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRequest request) {
        if (orderId == null) {
            throw new OrderException.NotFound();
        }
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        
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