package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Profile({"local", "test", "dev", "prod"})
@RequiredArgsConstructor
public class CouponJpaRepository implements CouponRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public Optional<Coupon> findById(Long id) {
        try {
            Coupon coupon = entityManager.find(Coupon.class, id);
            return Optional.ofNullable(coupon);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            entityManager.persist(coupon);
            return coupon;
        } else {
            return entityManager.merge(coupon);
        }
    }

    @Override
    public List<Coupon> findByStatus(CouponStatus status) {
        return entityManager.createQuery(
            "SELECT c FROM Coupon c WHERE c.status = :status", Coupon.class)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public List<Coupon> findExpiredCouponsNotInStatus(LocalDateTime now, CouponStatus... excludeStatuses) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM Coupon c WHERE c.endDate < :now");
        
        if (excludeStatuses != null && excludeStatuses.length > 0) {
            jpql.append(" AND c.status NOT IN :excludeStatuses");
        }
        
        var query = entityManager.createQuery(jpql.toString(), Coupon.class)
            .setParameter("now", now);
            
        if (excludeStatuses != null && excludeStatuses.length > 0) {
            query.setParameter("excludeStatuses", List.of(excludeStatuses));
        }
        
        return query.getResultList();
    }

    @Override
    public long countByStatus(CouponStatus status) {
        return entityManager.createQuery(
            "SELECT COUNT(c) FROM Coupon c WHERE c.status = :status", Long.class)
            .setParameter("status", status)
            .getSingleResult();
    }
}