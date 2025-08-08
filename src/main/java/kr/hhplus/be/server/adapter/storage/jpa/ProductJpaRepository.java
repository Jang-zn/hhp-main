package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Repository
@Profile({"local", "test", "dev", "prod", "integration-test"})
@RequiredArgsConstructor
public class ProductJpaRepository implements ProductRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public Optional<Product> findById(Long id) {
        try {
            Product product = entityManager.find(Product.class, id);
            return Optional.ofNullable(product);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        try {
            Product product = entityManager.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(product);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Product> findByIdsWithLock(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            TypedQuery<Product> query = entityManager.createQuery(
                "SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id", Product.class);
            query.setParameter("ids", ids);
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            // 락 타임아웃 3초 설정
            query.setHint("javax.persistence.lock.timeout", 3000);
            return query.getResultList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            entityManager.persist(product);
            return product;
        } else {
            return entityManager.merge(product);
        }
    }

    @Override
    public List<Product> findPopularProducts(int period) {
        return entityManager.createQuery(
            "SELECT p FROM Product p WHERE p.createdAt >= :periodDate ORDER BY p.createdAt DESC", Product.class)
            .setParameter("periodDate", java.time.LocalDateTime.now().minusDays(period))
            .getResultList();
    }

    @Override
    public List<Product> findAllWithPagination(int limit, int offset) {
        return entityManager.createQuery(
            "SELECT p FROM Product p ORDER BY p.createdAt DESC", Product.class)
            .setMaxResults(limit)
            .setFirstResult(offset)
            .getResultList();
    }
}