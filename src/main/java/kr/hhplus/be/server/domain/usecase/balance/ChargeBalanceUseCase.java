package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ChargeBalanceUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    
    public Balance execute(Long userId, BigDecimal amount) {
        String lockKey = "balance-charge-" + userId;
        if (!lockingPort.acquireLock(lockKey)) {
            throw new RuntimeException("Failed to acquire lock");
        }
        try {
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Balance balance = balanceRepositoryPort.findByUser(user)
                    .orElse(Balance.builder().user(user).amount(BigDecimal.ZERO).build());
            balance.addAmount(amount);
            Balance savedBalance = balanceRepositoryPort.save(balance);
            cachePort.put("balance:" + userId, savedBalance, 600);
            return savedBalance;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
} 