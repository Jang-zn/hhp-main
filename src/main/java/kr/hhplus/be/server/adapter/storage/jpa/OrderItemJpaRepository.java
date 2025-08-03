package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;

@Repository
@Profile({"local", "test", "dev", "prod", "integration-test"})
@RequiredArgsConstructor
public class OrderItemJpaRepository implements OrderItemRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            entityManager.persist(orderItem);
            return orderItem;
        } else {
            return entityManager.merge(orderItem);
        }
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        for (OrderItem orderItem : orderItems) {
            save(orderItem);
        }
        return orderItems;
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return entityManager.createQuery(
            "SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId", OrderItem.class)
            .setParameter("orderId", orderId)
            .getResultList();
    }
}