package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.usecase.GetCouponsUseCase;
import kr.hhplus.be.server.domain.usecase.AcquireCouponUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 쿠폰 관리 Controller
 * 쿠폰 발급 및 조회 기능을 제공합니다.
 */
@Tag(name = "쿠폰 관리", description = "쿠폰 발급 및 조회 API")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {
    private final AcquireCouponUseCase acquireCouponUseCase;
    private final GetCouponsUseCase getCouponsUseCase;

    @ApiSuccess(summary = "쿠폰 발급")
    @PostMapping("/acquire")
    public CouponResponse acquireCoupon(
            @RequestParam Long userId,
            @RequestParam Long couponId) {
        return acquireCouponUseCase.execute(userId, couponId);
    }

    @ApiSuccess(summary = "보유 쿠폰 조회")
    @GetMapping("/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return getCouponsUseCase.execute(userId, limit, offset);
    }
} 