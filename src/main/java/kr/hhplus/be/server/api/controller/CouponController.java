package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 쿠폰 관리 Controller
 * 쿠폰 발급 및 조회 기능을 제공합니다.
 */
@Tag(name = "쿠폰 관리", description = "쿠폰 발급 및 조회 API")
@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    @ApiSuccess(summary = "쿠폰 발급")
    @PostMapping("/issue")
    public CouponResponse issueCoupon(
            @RequestParam Long userId,
            @RequestParam Long couponId) {
        // TODO: 선착순 쿠폰 발급 로직 구현 (userId, couponId)
        // Coupon coupon = issueCouponUseCase.execute(userId, couponId);
        return new CouponResponse(1L, "WELCOME10", new java.math.BigDecimal("10"), 
                java.time.LocalDateTime.now().plusDays(30));
    }

    @ApiSuccess(summary = "보유 쿠폰 조회")
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