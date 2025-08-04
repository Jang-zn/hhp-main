package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.Optional;

@Repository
@Profile({"local", "test", "dev", "prod", "integration-test"})
@RequiredArgsConstructor
public class BalanceJpaRepository implements BalanceRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public Optional<Balance> findByUserId(Long userId) {
        try {
            Balance balance = entityManager.createQuery(
                "SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class)
                .setParameter("userId", userId)
                .getSingleResult();
            return Optional.of(balance);
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Balance save(Balance balance) {
        if (balance.getId() == null) {
            entityManager.persist(balance);
            return balance;
        } else {
            return entityManager.merge(balance);
        }
    }
}