package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import org.springframework.stereotype.Repository;
import kr.hhplus.be.server.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.hhplus.be.server.domain.exception.OrderException;


@Repository
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public Order save(Order order) {
        if (order == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        if (order.getUser() == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        if (order.getUser().getId() == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long orderId = order.getId() != null ? order.getId() : nextId.getAndIncrement();
        
        Order savedOrder = orders.compute(orderId, (key, existingOrder) -> {
            if (existingOrder != null) {
                order.onUpdate();
                order.setId(existingOrder.getId());
                order.setCreatedAt(existingOrder.getCreatedAt());
                return order;
            } else {
                order.onCreate();
                if (order.getId() == null) {
                    order.setId(orderId);
                }
                return order;
            }
        });
        
        return savedOrder;
    }

    @Override
    public List<Order> findByUser(kr.hhplus.be.server.domain.entity.User user) {
        if (user == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        if (user.getId() == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        
        return orders.values().stream()
                .filter(order -> 
                    order.getUser() != null && 
                    order.getUser().getId().equals(user.getId()))
                .toList();
    }

    @Override
    public Optional<Order> findByIdAndUser(Long orderId, User user) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        if (user == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        if (user.getId() == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        
        return Optional.ofNullable(orders.get(orderId))
                .filter(order -> 
                    order.getUser() != null && 
                    order.getUser().getId().equals(user.getId()));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        
        return Optional.ofNullable(orders.get(orderId));
    }
} 