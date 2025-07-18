package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
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
    
    /**
     * Initiates a balance charge operation for the specified user and amount.
     *
     * @param userId the ID of the user whose balance will be charged
     * @param amount the amount to add to the user's balance
     * @return the updated Balance after charging, or null if not implemented
     */
    public Balance execute(Long userId, BigDecimal amount) {
        // TODO: 잔액 충전 로직 구현
        return null;
    }
} 