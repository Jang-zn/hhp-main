package kr.hhplus.be.server.api.controller;

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
        return ResponseEntity.created(null).body(CommonResponse.success("주문이 생성되었습니다.", null));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<CommonResponse<Object>> payOrder(
            @PathVariable Long orderId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long couponId) {
        // TODO: 결제 처리 로직 구현
        return ResponseEntity.ok(CommonResponse.success("결제가 완료되었습니다.", null));
    }
} 