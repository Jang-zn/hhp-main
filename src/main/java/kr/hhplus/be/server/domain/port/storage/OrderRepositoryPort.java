package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    /**
 * Retrieves an order by its unique identifier.
 *
 * @param id the unique identifier of the order
 * @return an {@code Optional} containing the order if found, or empty if not present
 */
Optional<Order> findById(Long id);
    /**
 * Retrieves all orders associated with the specified user ID.
 *
 * @param userId the unique identifier of the user whose orders are to be retrieved
 * @return a list of orders belonging to the given user; the list may be empty if no orders are found
 */
List<Order> findByUserId(Long userId);
    /**
 * Persists a new order entity and returns the saved instance.
 *
 * @param order the order to be saved
 * @return the saved order entity
 */
Order save(Order order);
    /**
 * Updates the status of the order identified by the given order ID.
 *
 * @param orderId the unique identifier of the order to update
 * @param status the new status to set for the order
 * @return the updated Order entity with the new status
 */
Order updateStatus(Long orderId, String status);
} 