package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcquireCouponUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponRepositoryPort couponRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final LockingPort lockingPort;
    
    public CouponHistory execute(Long userId, Long couponId) {
        String lockKey = "coupon-acquire-" + couponId;
        if (!lockingPort.acquireLock(lockKey)) {
            throw new RuntimeException("Failed to acquire lock");
        }
        try {
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Coupon coupon = couponRepositoryPort.findById(couponId)
                    .orElseThrow(() -> new RuntimeException("Coupon not found"));

            if (couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)) {
                throw new RuntimeException("Coupon already acquired");
            }

            coupon.decreaseStock(1);
            Coupon savedCoupon = couponRepositoryPort.save(coupon);

            CouponHistory couponHistory = CouponHistory.builder()
                    .user(user)
                    .coupon(savedCoupon)
                    .build();
            return couponHistoryRepositoryPort.save(couponHistory);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
} 