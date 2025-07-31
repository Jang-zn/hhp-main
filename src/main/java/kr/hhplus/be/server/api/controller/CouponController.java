package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.docs.annotation.CouponApiDocs;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.UserException;
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

    @CouponApiDocs(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다")
    @PostMapping("/issue")
    public CouponResponse issueCoupon(@RequestBody CouponRequest request) {
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        request.validate();
        if (request.getUserId() == null || request.getCouponId() == null) {
            throw new CouponException.UserIdAndCouponIdRequired();
        }
        
        CouponHistory couponHistory = issueCouponUseCase.execute(request.getUserId(), request.getCouponId());
        return new CouponResponse(
                couponHistory.getId(),
                couponHistory.getCoupon().getId(),
                couponHistory.getCoupon().getCode(),
                couponHistory.getCoupon().getDiscountRate(),
                couponHistory.getCoupon().getEndDate(),
                couponHistory.getCoupon().getStatus(),
                couponHistory.getStatus(),
                couponHistory.getIssuedAt(),
                couponHistory.getUsedAt(),
                couponHistory.canUse()
        );
    }

    @CouponApiDocs(summary = "보유 쿠폰 조회", description = "사용자가 보유한 쿠폰 목록을 조회합니다")
    @GetMapping("/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable Long userId,
            CouponRequest request) {
        if (userId == null) {
            throw new UserException.UserIdCannotBeNull();
        }
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        request.validatePagination();
        
        List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, request.getLimit(), request.getOffset());
        return couponHistories.stream()
                .map(history -> new CouponResponse(
                        history.getId(),
                        history.getCoupon().getId(),
                        history.getCoupon().getCode(),
                        history.getCoupon().getDiscountRate(),
                        history.getCoupon().getEndDate(),
                        history.getCoupon().getStatus(),
                        history.getStatus(),
                        history.getIssuedAt(),
                        history.getUsedAt(),
                        history.canUse()
                ))
                .collect(Collectors.toList());
    }
} 