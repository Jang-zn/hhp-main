package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepositoryPort {
    Optional<Coupon> findById(Long id);
    
    /**
     * 비관적 락을 사용하여 쿠폰을 조회합니다.
     * SELECT ... FOR UPDATE로 배타 락을 획득하여 동시 발급 시 경합을 방지합니다.
     * 
     * @param id 쿠폰 ID
     * @return 락이 걸린 쿠폰 엔티티
     */
    Optional<Coupon> findByIdWithLock(Long id);
    
    Coupon save(Coupon coupon);
    
    /**
     * 특정 상태의 쿠폰들을 조회합니다.
     */
    List<Coupon> findByStatus(CouponStatus status);
    
    /**
     * 만료되었지만 특정 상태가 아닌 쿠폰들을 조회합니다.
     */
    List<Coupon> findExpiredCouponsNotInStatus(LocalDateTime now, CouponStatus... excludeStatuses);
    
    /**
     * 상태별 쿠폰 수를 조회합니다.
     */
    long countByStatus(CouponStatus status);
} 