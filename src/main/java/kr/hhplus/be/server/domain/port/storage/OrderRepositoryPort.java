package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    List<Order> findByUserId(Long userId);
    List<Order> findByUserId(Long userId, int limit, int offset);
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);
    Optional<Order> findById(Long orderId);
} 