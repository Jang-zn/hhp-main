package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;

import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    Optional<CouponHistory> findById(String id);
    List<CouponHistory> findByUserId(String userId, int limit, int offset);
    CouponHistory save(CouponHistory couponHistory);
    boolean existsByUserIdAndCouponId(String userId, String couponId);
} 