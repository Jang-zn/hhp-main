package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;

import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    boolean existsByUserAndCoupon(kr.hhplus.be.server.domain.entity.User user, kr.hhplus.be.server.domain.entity.Coupon coupon);
    CouponHistory save(CouponHistory couponHistory);
    List<CouponHistory> findByUserWithPagination(kr.hhplus.be.server.domain.entity.User user, int limit, int offset);
} 