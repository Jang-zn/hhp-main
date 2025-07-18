package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Balance;

import java.util.Optional;

public interface BalanceRepositoryPort {
    Optional<Balance> findByUserId(String userId);
    Balance save(Balance balance);
    Balance updateAmount(String userId, java.math.BigDecimal amount);
} 