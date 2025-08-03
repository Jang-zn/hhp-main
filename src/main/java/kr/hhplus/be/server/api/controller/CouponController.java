package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.docs.annotation.CouponApiDocs;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.facade.coupon.GetCouponListFacade;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;

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
    
    private final IssueCouponFacade issueCouponFacade;
    private final GetCouponListFacade getCouponListFacade;
    private final CouponRepositoryPort couponRepositoryPort;

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

        CouponHistory couponHistory = issueCouponFacade.issueCoupon(request.getUserId(), request.getCouponId());
        
        // Coupon 정보 조회
        Coupon coupon = couponRepositoryPort.findById(couponHistory.getCouponId())
                .orElseThrow(() -> new CouponException.NotFound());
        
        return new CouponResponse(
                couponHistory.getId(),
                couponHistory.getCouponId(),
                coupon.getCode(),
                coupon.getDiscountRate(),
                coupon.getEndDate(),
                coupon.getStatus(),
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

        List<CouponHistory> couponHistories = getCouponListFacade.getCouponList(userId, request.getLimit(), request.getOffset());
        return couponHistories.stream()
                .map(history -> {
                    // Coupon 정보 조회
                    Coupon coupon = couponRepositoryPort.findById(history.getCouponId())
                            .orElseThrow(() -> new CouponException.NotFound());
                    
                    return new CouponResponse(
                            history.getId(),
                            history.getCouponId(),
                            coupon.getCode(),
                            coupon.getDiscountRate(),
                            coupon.getEndDate(),
                            coupon.getStatus(),
                            history.getStatus(),
                            history.getIssuedAt(),
                            history.getUsedAt(),
                            history.canUse()
                    );
                })
                .collect(Collectors.toList());
    }
} 