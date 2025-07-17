package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.ApiMessage;
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
        // Coupon coupon = issueCouponUseCase.execute(userId, couponCode);
        return CommonResponse.ok(ApiMessage.COUPON_ISSUED.getMessage(), null); // 나중에 실제 coupon 데이터로 교체
    }

    @GetMapping("/{userId}")
    public ResponseEntity<CommonResponse<Object>> getCoupons(@PathVariable Long userId) {
        // TODO: 보유 쿠폰 목록 조회 로직 구현
        // List<Coupon> coupons = getCouponsUseCase.execute(userId);
        return CommonResponse.ok(ApiMessage.COUPONS_RETRIEVED.getMessage(), null); // 나중에 실제 coupons 데이터로 교체
    }
} 