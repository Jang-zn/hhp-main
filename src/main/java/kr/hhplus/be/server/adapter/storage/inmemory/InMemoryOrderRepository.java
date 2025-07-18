package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public List<Order> findByUser(kr.hhplus.be.server.domain.entity.User user) {
        return orders.values().stream()
                .filter(order -> order.getUser().equals(user))
                .toList();
    }

    @Override
    public Optional<Order> findByIdAndUser(Long orderId, kr.hhplus.be.server.domain.entity.User user) {
        return Optional.ofNullable(orders.get(orderId))
                .filter(order -> order.getUser().equals(user));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
} 