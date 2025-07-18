package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Optional<Order> findById(String id);
    List<Order> findByUserId(String userId);
    Order save(Order order);
    Order updateStatus(String orderId, String status);
} 