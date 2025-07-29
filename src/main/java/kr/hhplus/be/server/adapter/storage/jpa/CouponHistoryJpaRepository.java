package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponHistoryJpaRepository implements CouponHistoryRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public boolean existsByUserAndCoupon(User user, Coupon coupon) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(ch) FROM CouponHistory ch WHERE ch.user = :user AND ch.coupon = :coupon", Long.class)
            .setParameter("user", user)
            .setParameter("coupon", coupon)
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
    public List<CouponHistory> findByUserWithPagination(User user, int limit, int offset) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch WHERE ch.user = :user ORDER BY ch.createdAt DESC", CouponHistory.class)
            .setParameter("user", user)
            .setMaxResults(limit)
            .setFirstResult(offset)
            .getResultList();
    }

    @Override
    public List<CouponHistory> findByUserAndStatus(User user, CouponHistoryStatus status) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch WHERE ch.user = :user AND ch.status = :status", CouponHistory.class)
            .setParameter("user", user)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status) {
        return entityManager.createQuery(
            "SELECT ch FROM CouponHistory ch WHERE ch.coupon.endDate < :now AND ch.status = :status", CouponHistory.class)
            .setParameter("now", now)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public long countUsableCouponsByUser(User user) {
        return entityManager.createQuery(
            "SELECT COUNT(ch) FROM CouponHistory ch WHERE ch.user = :user AND ch.status = :status AND ch.coupon.endDate > :now", Long.class)
            .setParameter("user", user)
            .setParameter("status", CouponHistoryStatus.ISSUED)
            .setParameter("now", LocalDateTime.now())
            .getSingleResult();
    }
}