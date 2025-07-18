package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
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
    public CouponResponse acquireCoupon(@Valid @RequestBody CouponRequest request) {
        CouponHistory couponHistory = acquireCouponUseCase.execute(request.getUserId(), request.getCouponId());
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
            @Valid CouponRequest request) {
        List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, request.getLimit(), request.getOffset());
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