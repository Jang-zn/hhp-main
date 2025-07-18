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
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }
    
    @Override
    public List<Order> findByUserId(Long userId) {
        // TODO: 사용자별 주문 조회 로직 구현
        return new ArrayList<>();
    }
    
    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }
    
    @Override
    public Order updateStatus(Long orderId, String status) {
        Order order = orders.get(orderId);
        if (order != null) {
            // TODO: 실제 상태 업데이트 로직 구현
        }
        return order;
    }
} 