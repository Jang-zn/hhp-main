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
import java.time.LocalDateTime;
import kr.hhplus.be.server.domain.exception.PaymentException;

@Repository
public class InMemoryPaymentRepository implements PaymentRepositoryPort {
    
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    @Override
    public Optional<Payment> findById(Long id) {
        if (id == null) {
            throw new PaymentException.PaymentIdCannotBeNull();
        }
        return Optional.ofNullable(payments.get(id));
    }
    
    @Override
    public List<Payment> findByOrderId(Long orderId) {
        if (orderId == null) {
            throw new PaymentException.PaymentIdCannotBeNull();
        }
        return payments.values().stream()
                .filter(payment -> payment.getOrder() != null && payment.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
    }
    
    @Override
    public Payment save(Payment payment) {
        if (payment == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getUser() == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getOrder() == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getAmount() != null && payment.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new PaymentException.PaymentAmountCannotBeNegative();
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
            throw new PaymentException.PaymentIdCannotBeNull();
        }
        if (status == null) {
            throw new PaymentException.PaymentStatusCannotBeNull();
        }
        
        return payments.compute(paymentId, (key, existingPayment) -> {
            if (existingPayment != null) {
                return Payment.builder()
                        .id(existingPayment.getId())
                        .order(existingPayment.getOrder())
                        .user(existingPayment.getUser())
                        .amount(existingPayment.getAmount())
                        .status(status)
                        .createdAt(existingPayment.getCreatedAt())
                        .updatedAt(LocalDateTime.now())
                        .build();
            }
            return null;
        });
    }
}