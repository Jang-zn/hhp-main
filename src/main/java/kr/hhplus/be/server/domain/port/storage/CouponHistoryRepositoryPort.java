package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;

import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    Optional<CouponHistory> findById(Long id);
    List<CouponHistory> findByUserId(Long userId, int limit, int offset);
    CouponHistory save(CouponHistory couponHistory);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
} 