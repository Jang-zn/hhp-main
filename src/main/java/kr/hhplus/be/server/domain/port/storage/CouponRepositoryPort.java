package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepositoryPort extends JpaRepository<Coupon, Long> {
    /**
     * 특정 상태의 쿠폰들을 조회합니다.
     */
    List<Coupon> findByStatus(CouponStatus status);
    
    /**
     * 만료되었지만 특정 상태가 아닌 쿠폰들을 조회합니다.
     */
    @Query("SELECT c FROM Coupon c WHERE c.endDate < :now AND c.status NOT IN :excludeStatuses")
    List<Coupon> findExpiredCouponsNotInStatus(@Param("now") LocalDateTime now, 
                                               @Param("excludeStatuses") CouponStatus... excludeStatuses);
    
    /**
     * 상태별 쿠폰 수를 조회합니다.
     */
    long countByStatus(CouponStatus status);
} 