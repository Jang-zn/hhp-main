package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    /**
 * Retrieves a payment entity by its unique identifier.
 *
 * @param id the unique identifier of the payment
 * @return an {@code Optional} containing the payment if found, or empty if not found
 */
Optional<Payment> findById(Long id);
    /**
 * Retrieves all payments associated with the specified order ID.
 *
 * @param orderId the unique identifier of the order
 * @return a list of payments linked to the given order ID; the list may be empty if no payments are found
 */
List<Payment> findByOrderId(Long orderId);
    /**
 * Persists a new Payment entity and returns the saved instance.
 *
 * @param payment the Payment entity to be saved
 * @return the persisted Payment entity
 */
Payment save(Payment payment);
    /**
 * Updates the status of a payment identified by its ID.
 *
 * @param paymentId the unique identifier of the payment to update
 * @param status the new status to set for the payment
 * @return the updated Payment entity
 */
Payment updateStatus(Long paymentId, String status);
} 