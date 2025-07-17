package kr.hhplus.be.server.api.controller;

import jakarta.validation.constraints.NotNull;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupon")
@Validated
public class CouponController {

    @PostMapping("/issue")
    public CouponResponse issueCoupon(
            @NotNull(message = "사용자 ID는 필수입니다") @RequestParam Long userId,
            @NotNull(message = "쿠폰 ID는 필수입니다") @RequestParam Long couponId) {
        // TODO: 선착순 쿠폰 발급 로직 구현 (userId, couponId)
        // Coupon coupon = issueCouponUseCase.execute(userId, couponId);
        return new CouponResponse(1L, "WELCOME10", new java.math.BigDecimal("10"), 
                java.time.LocalDateTime.now().plusDays(30));
    }

    @GetMapping("/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // TODO: 보유 쿠폰 목록 조회 로직 구현
        // List<Coupon> coupons = getCouponsUseCase.execute(userId, limit, offset);
        return List.of(
                new CouponResponse(1L, "WELCOME10", new java.math.BigDecimal("10"), 
                        java.time.LocalDateTime.now().plusDays(30)),
                new CouponResponse(2L, "VIP20", new java.math.BigDecimal("20"), 
                        java.time.LocalDateTime.now().plusDays(7))
        );
    }
} 