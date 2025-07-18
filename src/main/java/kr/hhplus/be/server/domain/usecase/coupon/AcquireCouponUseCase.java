package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
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
    
    /**
     * Initiates the process of acquiring a coupon for a user.
     *
     * @param userId   the ID of the user requesting the coupon
     * @param couponId the ID of the coupon to be acquired
     * @return the coupon history record if acquisition is successful, or {@code null} if not implemented
     */
    public CouponHistory execute(Long userId, Long couponId) {
        // TODO: 쿠폰 발급 로직 구현
        return null;
    }
} 