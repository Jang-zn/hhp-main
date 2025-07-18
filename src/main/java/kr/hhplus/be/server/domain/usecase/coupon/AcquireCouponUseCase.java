package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcquireCouponUseCase {
    
    private final StoragePort storagePort;
    private final LockingPort lockingPort;
    
    public CouponHistory execute(Long userId, Long couponId) {
        // TODO: 쿠폰 발급 로직 구현
        return null;
    }
} 