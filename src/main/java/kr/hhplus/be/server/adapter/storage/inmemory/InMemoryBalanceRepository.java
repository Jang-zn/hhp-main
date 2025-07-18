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
    
    /**
     * Retrieves the balance associated with the specified user ID.
     *
     * @param userId the ID of the user whose balance is to be retrieved
     * @return an {@code Optional} containing the user's balance if present, or an empty {@code Optional} if not found
     */
    @Override
    public Optional<Balance> findByUserId(Long userId) {
        return Optional.ofNullable(balances.get(userId));
    }
    
    /**
     * Stores or updates the given Balance in the in-memory repository and returns the saved Balance.
     *
     * @param balance the Balance entity to be saved or updated
     * @return the saved Balance entity
     */
    @Override
    public Balance save(Balance balance) {
        balances.put(balance.getUser().getId(), balance);
        return balance;
    }
    
    /**
     * Retrieves the balance for the specified user ID and is intended to update its amount, but currently returns the existing balance without modification.
     *
     * @param userId the ID of the user whose balance is to be updated
     * @param amount the amount intended for the update (currently unused)
     * @return the existing Balance for the user, or {@code null} if not found
     */
    @Override
    public Balance updateAmount(Long userId, BigDecimal amount) {
        Balance balance = balances.get(userId);
        if (balance != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return balance;
    }
} 