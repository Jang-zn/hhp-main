package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetBalanceUseCase {
    
    private final StoragePort storagePort;
    private final CachePort cachePort;
    
    public Optional<Balance> execute(Long userId) {
        // TODO: 잔액 조회 로직 구현
        return Optional.empty();
    }
} 