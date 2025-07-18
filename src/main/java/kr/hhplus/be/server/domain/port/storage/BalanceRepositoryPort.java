package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Balance;

import java.util.Optional;

public interface BalanceRepositoryPort {
    /**
 * Retrieves the balance entity associated with the specified user ID.
 *
 * @param userId the unique identifier of the user
 * @return an {@code Optional} containing the balance if found, or empty if no balance exists for the user
 */
Optional<Balance> findByUserId(Long userId);
    /**
 * Persists the given Balance entity and returns the saved instance.
 *
 * @param balance the Balance entity to be saved
 * @return the persisted Balance entity
 */
Balance save(Balance balance);
    /**
 * Updates the balance amount for the specified user ID and returns the updated Balance entity.
 *
 * @param userId the ID of the user whose balance will be updated
 * @param amount the new balance amount to set
 * @return the updated Balance entity
 */
Balance updateAmount(Long userId, java.math.BigDecimal amount);
} 