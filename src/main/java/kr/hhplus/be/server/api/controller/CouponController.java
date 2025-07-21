package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
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
    
    private final IssueCouponUseCase issueCouponUseCase;
    private final GetCouponListUseCase getCouponListUseCase;

    @ApiSuccess(summary = "쿠폰 발급")
    @PostMapping("/issue")
    public CouponResponse issueCoupon(@Valid @RequestBody CouponRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getUserId() == null || request.getCouponId() == null) {
            throw new IllegalArgumentException("UserId and CouponId are required");
        }
        
        CouponHistory couponHistory = issueCouponUseCase.execute(request.getUserId(), request.getCouponId());
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
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getLimit() < 0 || request.getOffset() < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        
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