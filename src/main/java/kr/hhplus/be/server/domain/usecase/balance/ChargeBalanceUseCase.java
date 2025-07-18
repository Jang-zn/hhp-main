package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ChargeBalanceUseCase {
    
    private final StoragePort storagePort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    
    public Balance execute(Long userId, BigDecimal amount) {
        // TODO: 잔액 충전 로직 구현
        return null;
    }
} 