package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Optional<Order> findById(Long id);
    List<Order> findByUserId(Long userId);
    Order save(Order order);
    Order updateStatus(Long orderId, String status);
} 