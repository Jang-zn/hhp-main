package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
@Profile("test_inmemory")
public class InMemoryOrderItemRepository implements OrderItemRepositoryPort {

    private final Map<Long, OrderItem> orderItems = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("OrderItem cannot be null");
        }
        
        if (orderItem.getId() == null) {
            orderItem = OrderItem.builder()
                    .id(nextId.getAndIncrement())
                    .orderId(orderItem.getOrderId())
                    .productId(orderItem.getProductId())
                    .quantity(orderItem.getQuantity())
                    .price(orderItem.getPrice())
                    .build();
        }
        
        orderItems.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItems.values().stream()
                .filter(orderItem -> orderItem.getOrderId().equals(orderId))
                .collect(Collectors.toList());
    }
    
    // 테스트용 메소드
    public void clear() {
        orderItems.clear();
        nextId.set(1L);
    }
}