package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public interface CouponHistoryRepositoryPort extends JpaRepository<CouponHistory, Long> {
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    
    @Query("SELECT ch FROM CouponHistory ch WHERE ch.userId = :userId")
    List<CouponHistory> findByUserIdWithPagination(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 사용자의 특정 상태 쿠폰 히스토리를 조회합니다.
     */
    List<CouponHistory> findByUserIdAndStatus(Long userId, CouponHistoryStatus status);
    
    /**
     * 만료되었지만 특정 상태인 쿠폰 히스토리들을 조회합니다.
     */
    @Query("SELECT ch FROM CouponHistory ch JOIN Coupon c ON ch.couponId = c.id " +
           "WHERE c.endDate < :now AND ch.status = :status")
    List<CouponHistory> findExpiredHistoriesInStatus(@Param("now") LocalDateTime now, 
                                                     @Param("status") CouponHistoryStatus status);
    
    /**
     * 사용자의 사용 가능한 쿠폰 개수를 조회합니다.
     */
    @Query("SELECT COUNT(ch) FROM CouponHistory ch WHERE ch.userId = :userId AND ch.status = 'ISSUED'")
    long countUsableCouponsByUserId(@Param("userId") Long userId);
} 