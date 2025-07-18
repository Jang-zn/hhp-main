package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepositoryPort {
    Optional<Coupon> findById(Long id);
    Coupon save(Coupon coupon);
    Coupon updateIssuedCount(Long couponId, int issuedCount);
    List<Coupon> findApplicableProducts(Long couponId);
} 