package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepositoryPort {
    
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    
    /**
     * Retrieves a payment by its unique ID.
     *
     * @param id the unique identifier of the payment
     * @return an {@code Optional} containing the payment if found, or empty if not present
     */
    @Override
    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(payments.get(id));
    }
    
    /**
     * Returns a list of payments associated with the specified order ID.
     *
     * <p>Currently, this method is not implemented and always returns an empty list.</p>
     *
     * @param orderId the ID of the order to search payments for
     * @return an empty list, as the retrieval logic is not yet implemented
     */
    @Override
    public List<Payment> findByOrderId(Long orderId) {
        // TODO: 주문별 결제 조회 로직 구현
        return new ArrayList<>();
    }
    
    /**
     * Stores the given payment in memory and returns the saved payment.
     *
     * @param payment the payment to be saved
     * @return the saved payment
     */
    @Override
    public Payment save(Payment payment) {
        payments.put(payment.getId(), payment);
        return payment;
    }
    
    /**
     * Retrieves a payment by its ID and is intended to update its status.
     *
     * Currently, the status update logic is not implemented; the method returns the payment as is.
     *
     * @param paymentId the ID of the payment to update
     * @param status the new status to set for the payment
     * @return the payment associated with the given ID, or {@code null} if not found
     */
    @Override
    public Payment updateStatus(Long paymentId, String status) {
        Payment payment = payments.get(paymentId);
        if (payment != null) {
            // TODO: 실제 상태 업데이트 로직 구현
        }
        return payment;
    }
} 