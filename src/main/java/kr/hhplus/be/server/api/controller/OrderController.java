package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.ApiMessage;
import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @PostMapping
    public ResponseEntity<CommonResponse<Object>> createOrder(
            @RequestParam Long userId,
            @RequestParam List<Long> productIds,
            @RequestParam List<Integer> quantities) {
        // TODO: 주문 생성 로직 구현
        // Order order = createOrderUseCase.execute(userId, productIds, quantities);
        return CommonResponse.created(ApiMessage.ORDER_CREATED.getMessage(), null); // 나중에 실제 order 데이터로 교체
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<CommonResponse<Object>> payOrder(
            @PathVariable Long orderId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long couponId) {
        // TODO: 결제 처리 로직 구현
        // Order paidOrder = payOrderUseCase.execute(orderId, userId, couponId);
        return CommonResponse.ok(ApiMessage.PAYMENT_COMPLETED.getMessage(), null); // 나중에 실제 paidOrder 데이터로 교체
    }
} 