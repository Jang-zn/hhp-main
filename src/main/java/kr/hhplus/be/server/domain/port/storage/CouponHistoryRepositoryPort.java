package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    CouponHistory save(CouponHistory couponHistory);
    Optional<CouponHistory> findById(Long id);
    List<CouponHistory> findByUserIdWithPagination(Long userId, int limit, int offset);
    
    /**
     * 사용자의 특정 상태 쿠폰 히스토리를 조회합니다.
     */
    List<CouponHistory> findByUserIdAndStatus(Long userId, CouponHistoryStatus status);
    
    /**
     * 만료되었지만 특정 상태인 쿠폰 히스토리들을 조회합니다.
     */
    List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status);
    
    /**
     * 사용자의 사용 가능한 쿠폰 개수를 조회합니다.
     */
    long countUsableCouponsByUserId(Long userId);
} 