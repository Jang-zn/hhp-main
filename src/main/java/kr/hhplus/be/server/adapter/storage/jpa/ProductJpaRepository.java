package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

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