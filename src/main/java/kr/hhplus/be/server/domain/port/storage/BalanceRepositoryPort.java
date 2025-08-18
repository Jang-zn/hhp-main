package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Balance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BalanceRepositoryPort extends JpaRepository<Balance, Long> {
    Optional<Balance> findByUserId(Long userId);
} 