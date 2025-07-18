package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.AcquireCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;

import java.util.stream.Collectors;
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
        CouponHistory couponHistory = acquireCouponUseCase.execute(userId, couponId);
        return new CouponResponse(
                couponHistory.getCoupon().getId(),
                couponHistory.getCoupon().getCode(),
                couponHistory.getCoupon().getDiscountRate(),
                couponHistory.getCoupon().getEndDate()
        );
    }

    @ApiSuccess(summary = "보유 쿠폰 조회")
    @GetMapping("/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, limit, offset);
        return couponHistories.stream()
                .map(history -> new CouponResponse(
                        history.getCoupon().getId(),
                        history.getCoupon().getCode(),
                        history.getCoupon().getDiscountRate(),
                        history.getCoupon().getEndDate()
                ))
                .collect(Collectors.toList());
    }
} 