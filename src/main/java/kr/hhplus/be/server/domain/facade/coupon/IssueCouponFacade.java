package kr.hhplus.be.server.domain.facade.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IssueCouponFacade {

    private final IssueCouponUseCase issueCouponUseCase;
    private final LockingPort lockingPort;

    public IssueCouponFacade(IssueCouponUseCase issueCouponUseCase, LockingPort lockingPort) {
        this.issueCouponUseCase = issueCouponUseCase;
        this.lockingPort = lockingPort;
    }

    @Transactional
    public CouponHistory issueCoupon(Long userId, Long couponId) {
        String lockKey = "coupon-" + couponId;
        
        // 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return issueCouponUseCase.execute(userId, couponId);
        } finally {
            // 락 해제
            lockingPort.releaseLock(lockKey);
        }
    }
}