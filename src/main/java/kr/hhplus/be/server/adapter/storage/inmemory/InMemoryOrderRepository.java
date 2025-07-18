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
    
    /**
     * Retrieves an order by its unique ID.
     *
     * @param id the unique identifier of the order
     * @return an {@code Optional} containing the order if found, or empty if not present
     */
    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }
    
    /**
     * Returns a list of orders associated with the specified user ID.
     *
     * <p>Currently, this method is not implemented and always returns an empty list.</p>
     *
     * @param userId the ID of the user whose orders are to be retrieved
     * @return a list of orders for the given user ID, or an empty list if none are found or the method is unimplemented
     */
    @Override
    public List<Order> findByUserId(Long userId) {
        // TODO: 사용자별 주문 조회 로직 구현
        return new ArrayList<>();
    }
    
    /**
     * Saves the given order in the in-memory repository, inserting or updating it by its ID.
     *
     * @param order the order to be saved
     * @return the saved order
     */
    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }
    
    /**
     * Retrieves an order by its ID and is intended to update its status.
     *
     * <p>Currently, this method does not modify the order's status and simply returns the order if found, or {@code null} if not found.</p>
     *
     * @param orderId the ID of the order to update
     * @param status the new status to set for the order
     * @return the order with the specified ID, or {@code null} if no such order exists
     */
    @Override
    public Order updateStatus(Long orderId, String status) {
        Order order = orders.get(orderId);
        if (order != null) {
            // TODO: 실제 상태 업데이트 로직 구현
        }
        return order;
    }
} 