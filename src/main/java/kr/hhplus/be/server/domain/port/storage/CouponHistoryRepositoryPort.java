package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    boolean existsByUserAndCoupon(User user, Coupon coupon);
    CouponHistory save(CouponHistory couponHistory);
    Optional<CouponHistory> findById(Long id);
    List<CouponHistory> findByUserWithPagination(User user, int limit, int offset);
    
    /**
     * 사용자의 특정 상태 쿠폰 히스토리를 조회합니다.
     */
    List<CouponHistory> findByUserAndStatus(User user, CouponHistoryStatus status);
    
    /**
     * 만료되었지만 특정 상태인 쿠폰 히스토리들을 조회합니다.
     */
    List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status);
    
    /**
     * 사용자의 사용 가능한 쿠폰 개수를 조회합니다.
     */
    long countUsableCouponsByUser(User user);
} 