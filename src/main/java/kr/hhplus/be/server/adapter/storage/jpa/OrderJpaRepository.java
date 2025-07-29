package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@Repository
@Profile({"local", "test", "dev", "prod"})
@RequiredArgsConstructor
public class OrderJpaRepository implements OrderRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            entityManager.persist(order);
            return order;
        } else {
            return entityManager.merge(order);
        }
    }

    @Override
    public List<Order> findByUser(User user) {
        return entityManager.createQuery(
            "SELECT o FROM Order o WHERE o.user = :user ORDER BY o.createdAt DESC", Order.class)
            .setParameter("user", user)
            .getResultList();
    }

    @Override
    public Optional<Order> findByIdAndUser(Long orderId, User user) {
        try {
            Order order = entityManager.createQuery(
                "SELECT o FROM Order o WHERE o.id = :orderId AND o.user = :user", Order.class)
                .setParameter("orderId", orderId)
                .setParameter("user", user)
                .getSingleResult();
            return Optional.of(order);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        try {
            Order order = entityManager.find(Order.class, orderId);
            return Optional.ofNullable(order);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}