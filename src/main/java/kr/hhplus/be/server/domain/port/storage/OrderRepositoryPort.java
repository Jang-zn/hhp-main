package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    List<Order> findByUser(kr.hhplus.be.server.domain.entity.User user);
    Optional<Order> findByIdAndUser(Long orderId, kr.hhplus.be.server.domain.entity.User user);
    Optional<Order> findById(Long orderId);
} 