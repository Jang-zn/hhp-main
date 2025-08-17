package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.api.docs.annotation.CouponApiDocs;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 관리 Controller
 * 쿠폰 발급 및 조회 기능을 제공합니다.
 */
@Tag(name = "쿠폰 관리", description = "쿠폰 발급 및 조회 API")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CouponController {
    
    private final CouponService couponService;
    private final CouponRepositoryPort couponRepositoryPort;

    @CouponApiDocs(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다")
    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse issueCoupon(@Valid @RequestBody CouponRequest request) {
        CouponHistory couponHistory = couponService.issueCoupon(request.getCouponId(), request.getUserId());
        Coupon coupon = couponRepositoryPort.findById(couponHistory.getCouponId())
                .orElseThrow(() -> new CouponException.NotFound());
        
        return new CouponResponse(
                couponHistory.getId(),
                couponHistory.getUserId(),
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
    @GetMapping("/user/{userId}")
    public List<CouponResponse> getCoupons(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset) {
        List<CouponHistory> couponHistories = couponService.getCouponList(userId, limit, offset);
        return couponHistories.stream()
                .map(history -> safeCouponLookup(history))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
    
    /**
     * 쿠폰 정보를 안전하게 조회하고 CouponResponse를 생성합니다.
     * 쿠폰 조회에 실패해도 전체 요청이 실패하지 않도록 예외를 처리합니다.
     * 
     * @param history 쿠폰 이력
     * @return 성공 시 CouponResponse를 담은 Optional, 실패 시 Optional.empty()
     */
    private Optional<CouponResponse> safeCouponLookup(CouponHistory history) {
        try {
            Optional<Coupon> couponOpt = couponRepositoryPort.findById(history.getCouponId());
            
            if (couponOpt.isEmpty()) {
                log.warn("쿠폰 정보를 찾을 수 없습니다 - historyId: {}, couponId: {}", 
                        history.getId(), history.getCouponId());
                return Optional.empty();
            }
            
            Coupon coupon = couponOpt.get();
            CouponResponse response = new CouponResponse(
                    history.getId(),
                    history.getUserId(),
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
            
            return Optional.of(response);
            
        } catch (Exception e) {
            log.error("쿠폰 조회 중 예외 발생 - historyId: {}, couponId: {}", 
                    history.getId(), history.getCouponId(), e);
            return Optional.empty();
        }
    }
} 