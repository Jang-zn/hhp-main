package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    @PostMapping("/issue")
    public ResponseEntity<CommonResponse<Object>> issueCoupon(
            @RequestParam Long userId,
            @RequestParam String couponCode) {
        // TODO: 선착순 쿠폰 발급 로직 구현
        return ResponseEntity.ok(CommonResponse.success("쿠폰이 발급되었습니다.", null));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<CommonResponse<Object>> getCoupons(@PathVariable Long userId) {
        // TODO: 보유 쿠폰 목록 조회 로직 구현
        return ResponseEntity.ok(CommonResponse.success(null));
    }
} 