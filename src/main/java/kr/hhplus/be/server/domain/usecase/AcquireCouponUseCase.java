package kr.hhplus.be.server.domain.usecase;

import kr.hhplus.be.server.api.dto.response.CouponResponse;

public interface AcquireCouponUseCase {
    CouponResponse execute(Long userId, Long couponId);
} 