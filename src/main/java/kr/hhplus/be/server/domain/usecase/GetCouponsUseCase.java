package kr.hhplus.be.server.domain.usecase;

import kr.hhplus.be.server.api.dto.response.CouponResponse;

import java.util.List;

public interface GetCouponsUseCase {
    List<CouponResponse> execute(Long userId, int limit, int offset);
} 