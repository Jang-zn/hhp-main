package kr.hhplus.be.server.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
        Long couponId,
        String code,
        BigDecimal discountRate,
        LocalDateTime validUntil
) {} 