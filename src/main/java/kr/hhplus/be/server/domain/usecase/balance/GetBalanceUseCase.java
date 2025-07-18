package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
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
    
    public Optional<Balance> execute(Long userId) {
        return Optional.ofNullable(cachePort.get("balance:" + userId, Balance.class, () -> {
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return balanceRepositoryPort.findByUser(user).orElse(null);
        }));
    }
} 