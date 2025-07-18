package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {
    
    private final StoragePort storagePort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    
    public Order execute(Long userId, Map<Long, Integer> productQuantities) {
        // TODO: 주문 생성 로직 구현
        return null;
    }
} 