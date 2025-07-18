package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayOrderUseCase {
    
    private final StoragePort storagePort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    private final MessagingPort messagingPort;
    
    public Payment execute(Long orderId, Long userId, Long couponId) {
        // TODO: 주문 결제 로직 구현
        return null;
    }
} 