package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryPaymentRepository implements PaymentRepositoryPort {
    
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(payments.get(id));
    }
    
    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return payments.values().stream()
                .filter(payment -> payment.getOrder() != null && payment.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
    }
    
    @Override
    public Payment save(Payment payment) {
        payments.put(payment.getId(), payment);
        return payment;
    }
    
    @Override
    public Payment updateStatus(Long paymentId, PaymentStatus status) {
        Payment payment = payments.get(paymentId);
        if (payment != null) {
            payment.changeStatus(status);
            payments.put(paymentId, payment);
        }
        return payment;
    }
}