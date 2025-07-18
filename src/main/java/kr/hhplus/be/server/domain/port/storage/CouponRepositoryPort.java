package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepositoryPort {
    Optional<Coupon> findById(String id);
    Coupon save(Coupon coupon);
    Coupon updateIssuedCount(String couponId, int issuedCount);
    List<Coupon> findApplicableProducts(String couponId);
} 