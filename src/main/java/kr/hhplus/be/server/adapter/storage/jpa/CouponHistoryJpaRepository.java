package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Profile({"local", "test", "dev", "prod", "integration-test"})
@RequiredArgsConstructor
public class CouponHistoryJpaRepository implements CouponHistoryRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(ch) FROM CouponHistory ch WHERE ch.userId = :userId AND ch.couponId = :couponId", Long.class)
            .setParameter("userId", userId)
            .setParameter("couponId", couponId)
            .getSingleResult();
        return count > 0;
    }

    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        if (couponHistory.getId() == null) {
            entityManager.persist(couponHistory);
            return couponHistory;
        } else {
            return entityManager.merge(couponHistory);
        }
    }

    @Override
    public Optional<CouponHistory> findById(Long id) {
        try {
            CouponHistory couponHistory = entityManager.find(CouponHistory.class, id);
            return Optional.ofNullable(couponHistory);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<CouponHistory> findByUserIdWithPagination(Long userId, int limit, int offset) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch WHERE ch.userId = :userId ORDER BY ch.createdAt DESC", CouponHistory.class)
            .setParameter("userId", userId)
            .setMaxResults(limit)
            .setFirstResult(offset)
            .getResultList();
    }

    @Override
    public List<CouponHistory> findByUserIdAndStatus(Long userId, CouponHistoryStatus status) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch WHERE ch.userId = :userId AND ch.status = :status", CouponHistory.class)
            .setParameter("userId", userId)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch JOIN Coupon c ON ch.couponId = c.id WHERE c.endDate < :now AND ch.status = :status", CouponHistory.class)
            .setParameter("now", now)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public long countUsableCouponsByUserId(Long userId) {
        return entityManager.createQuery(
            "SELECT COUNT(ch) FROM CouponHistory ch JOIN Coupon c ON ch.couponId = c.id WHERE ch.userId = :userId AND ch.status = :status AND c.endDate > :now", Long.class)
            .setParameter("userId", userId)
            .setParameter("status", CouponHistoryStatus.ISSUED)
            .setParameter("now", LocalDateTime.now())
            .getSingleResult();
    }
}