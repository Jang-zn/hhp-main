package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryPaymentRepository implements PaymentRepositoryPort {
    
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    @Override
    public Optional<Payment> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        return Optional.ofNullable(payments.get(id));
    }
    
    @Override
    public List<Payment> findByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        return payments.values().stream()
                .filter(payment -> payment.getOrder() != null && payment.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
    }
    
    @Override
    public Payment save(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }
        if (payment.getUser() == null) {
            throw new IllegalArgumentException("Payment user cannot be null");
        }
        if (payment.getOrder() == null) {
            throw new IllegalArgumentException("Payment order cannot be null");
        }
        
        Long paymentId = payment.getId() != null ? payment.getId() : nextId.getAndIncrement();
        
        Payment savedPayment = payments.compute(paymentId, (key, existingPayment) -> {
            if (existingPayment != null) {
                return Payment.builder()
                        .id(existingPayment.getId())
                        .order(payment.getOrder())
                        .user(payment.getUser())
                        .amount(payment.getAmount())
                        .status(payment.getStatus())
                        .createdAt(existingPayment.getCreatedAt())
                        .updatedAt(payment.getUpdatedAt())
                        .build();
            } else {
                return Payment.builder()
                        .id(paymentId)
                        .order(payment.getOrder())
                        .user(payment.getUser())
                        .amount(payment.getAmount())
                        .status(payment.getStatus())
                        .createdAt(payment.getCreatedAt())
                        .updatedAt(payment.getUpdatedAt())
                        .build();
            }
        });
        
        return savedPayment;
    }
    
    @Override
    public Payment updateStatus(Long paymentId, PaymentStatus status) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Payment status cannot be null");
        }
        
        return payments.compute(paymentId, (key, existingPayment) -> {
            if (existingPayment != null) {
                existingPayment.changeStatus(status);
                return existingPayment;
            }
            return null;
        });
    }
}