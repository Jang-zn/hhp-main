package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.entity.User;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryBalanceRepository implements BalanceRepositoryPort {

    private final Map<Long, Balance> balances = new ConcurrentHashMap<>();

    @Override
    public Optional<Balance> findByUser(User user) {
        return balances.values().stream().filter(balance -> balance.getUser().equals(user)).findFirst();
    }

    @Override
    public Balance save(Balance balance) {
        balances.put(balance.getId(), balance);
        return balance;
    }
} 