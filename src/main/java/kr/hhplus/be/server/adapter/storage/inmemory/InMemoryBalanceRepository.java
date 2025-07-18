package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryBalanceRepository implements BalanceRepositoryPort {
    
    private final Map<Long, Balance> balances = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Balance> findByUserId(Long userId) {
        return Optional.ofNullable(balances.get(userId));
    }
    
    @Override
    public Balance save(Balance balance) {
        balances.put(balance.getUser().getId(), balance);
        return balance;
    }
    
    @Override
    public Balance updateAmount(Long userId, BigDecimal amount) {
        Balance balance = balances.get(userId);
        if (balance != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return balance;
    }
} 