package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public Order save(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (order.getUser() == null) {
            throw new IllegalArgumentException("Order user cannot be null");
        }
        if (order.getUser().getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long orderId = order.getId() != null ? order.getId() : nextId.getAndIncrement();
        
        Order savedOrder = orders.compute(orderId, (key, existingOrder) -> {
            if (existingOrder != null) {
                // 기존 주문 업데이트
                return Order.builder()
                        .id(existingOrder.getId())
                        .user(order.getUser())
                        .totalAmount(order.getTotalAmount())
                        .status(order.getStatus())
                        .items(order.getItems())
                        .payments(order.getPayments())
                        .createdAt(existingOrder.getCreatedAt())
                        .updatedAt(order.getUpdatedAt())
                        .build();
            } else {
                // 새로운 주문 생성
                return Order.builder()
                        .id(orderId)
                        .user(order.getUser())
                        .totalAmount(order.getTotalAmount())
                        .status(order.getStatus())
                        .items(order.getItems())
                        .payments(order.getPayments())
                        .createdAt(order.getCreatedAt())
                        .updatedAt(order.getUpdatedAt())
                        .build();
            }
        });
        
        return savedOrder;
    }

    @Override
    public List<Order> findByUser(kr.hhplus.be.server.domain.entity.User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        return orders.values().stream()
                .filter(order -> 
                    order.getUser() != null && 
                    order.getUser().getId().equals(user.getId()))
                .toList();
    }

    @Override
    public Optional<Order> findByIdAndUser(Long orderId, kr.hhplus.be.server.domain.entity.User user) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        return Optional.ofNullable(orders.get(orderId))
                .filter(order -> 
                    order.getUser() != null && 
                    order.getUser().getId().equals(user.getId()));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        
        return Optional.ofNullable(orders.get(orderId));
    }
} 