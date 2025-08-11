package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 쿠폰 조회, 발급 등의 기능을 제공하며,
 * 동시성 제어를 통해 쿠폰 재고 관리를 합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final GetCouponListUseCase getCouponListUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;

    /**
     * 사용자의 쿠폰 히스토리 목록 조회
     * 
     * @param userId 사용자 ID
     * @param limit 조회할 쿠폰 개수
     * @param offset 건너뛸 쿠폰 개수
     * @return 쿠폰 히스토리 목록
     */
    public List<CouponHistory> getCouponList(Long userId, int limit, int offset) {
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        return getCouponListUseCase.execute(userId, limit, offset);
    }

    /**
     * 쿠폰 발급
     * 
     * 동시성 제어를 위해 분산 락을 사용합니다.
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급된 쿠폰 히스토리
     */
    @Transactional
    public CouponHistory issueCoupon(Long couponId, Long userId) {
        String lockKey = "coupon-issue-" + couponId;
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return issueCouponUseCase.execute(couponId, userId);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
}