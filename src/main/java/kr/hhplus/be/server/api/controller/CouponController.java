package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.usecase.coupon.AcquireCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
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
    private final GetCouponListUseCase getCouponListUseCase;

    @ApiSuccess(summary = "쿠폰 발급")
    @PostMapping("/acquire")
    public CouponResponse acquireCoupon(
            @RequestParam Long userId,
            @RequestParam Long couponId) {
        // TODO: 쿠폰 발급 로직 구현
        // CouponHistory couponHistory = acquireCouponUseCase.execute(userId, couponId);
        return new CouponResponse(couponId, "COUPON123", new java.math.BigDecimal("0.1"), java.time.LocalDateTime.now().plusDays(30));
    }

    @ApiSuccess(summary = "보유 쿠폰 조회")
    @GetMapping("/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // TODO: 보유 쿠폰 조회 로직 구현
        // List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, limit, offset);
        return List.of(
                new CouponResponse(1L, "COUPON123", new java.math.BigDecimal("0.1"), java.time.LocalDateTime.now().plusDays(30)),
                new CouponResponse(2L, "COUPON456", new java.math.BigDecimal("0.2"), java.time.LocalDateTime.now().plusDays(60))
        );
    }
} 