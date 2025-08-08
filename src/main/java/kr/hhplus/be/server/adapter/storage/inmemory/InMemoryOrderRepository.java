package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.hhplus.be.server.domain.exception.OrderException;


@Repository
@Profile("test_inmemory")
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public Order save(Order order) {
        if (order == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        if (order.getUserId() == null) {
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
    public List<Order> findByUserId(Long userId) {
        if (userId == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        
        return orders.values().stream()
                .filter(order -> 
                    order.getUserId() != null && 
                    order.getUserId().equals(userId))
                .toList();
    }

    @Override
    public Optional<Order> findByIdAndUserId(Long orderId, Long userId) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        if (userId == null) {
            throw new OrderException.OrderCannotBeNull();
        }
        
        return Optional.ofNullable(orders.get(orderId))
                .filter(order -> 
                    order.getUserId() != null && 
                    order.getUserId().equals(userId));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        if (orderId == null) {
            throw new OrderException.OrderIdCannotBeNull();
        }
        
        return Optional.ofNullable(orders.get(orderId));
    }
} 