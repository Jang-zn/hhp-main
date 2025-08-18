package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepositoryPort extends JpaRepository<Coupon, Long> {
    List<Coupon> findByStatus(CouponStatus status);
    
    @Query("SELECT c FROM Coupon c WHERE c.endDate < :now AND c.status NOT IN (:excludeStatuses)")
    List<Coupon> findExpiredCouponsNotInStatus(@Param("now") LocalDateTime now, 
                                               @Param("excludeStatuses") Collection<CouponStatus> excludeStatuses);
    
    @Query("SELECT c FROM Coupon c WHERE c.endDate < :now")
    List<Coupon> findExpiredCoupons(@Param("now") LocalDateTime now);
    
    /**
     * 상태별 쿠폰 수를 조회합니다.
     */
    long countByStatus(CouponStatus status);
} 