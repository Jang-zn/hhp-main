package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetBalanceUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final CachePort cachePort;
    
    /**
     * Retrieves the balance for the specified user.
     *
     * @param userId the ID of the user whose balance is to be retrieved
     * @return an {@code Optional} containing the user's balance if found, or an empty {@code Optional} if not available
     */
    public Optional<Balance> execute(Long userId) {
        // TODO: 잔액 조회 로직 구현
        return Optional.empty();
    }
} 