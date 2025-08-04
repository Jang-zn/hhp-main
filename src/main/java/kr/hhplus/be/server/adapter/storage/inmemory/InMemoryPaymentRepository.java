package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import org.springframework.context.annotation.Profile;
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
@Profile("test_inmemory")
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
                .filter(payment -> payment.getOrderId() != null && payment.getOrderId().equals(orderId))
                .collect(Collectors.toList());
    }
    
    @Override
    public Payment save(Payment payment) {
        if (payment == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getUserId() == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getOrderId() == null) {
            throw new PaymentException.PaymentCannotBeNull();
        }
        if (payment.getAmount() != null && payment.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new PaymentException.PaymentAmountCannotBeNegative();
        }
        
        Long paymentId = payment.getId() != null ? payment.getId() : nextId.getAndIncrement();
        
        Payment savedPayment = payments.compute(paymentId, (key, existingPayment) -> {
            if (existingPayment != null) {
                payment.onUpdate();
                payment.setId(existingPayment.getId());
                payment.setCreatedAt(existingPayment.getCreatedAt());
                return payment;
            } else {
                payment.onCreate();
                if (payment.getId() == null) {
                    payment.setId(paymentId);
                }
                return payment;
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
                        .orderId(existingPayment.getOrderId())
                        .userId(existingPayment.getUserId())
                        .couponId(existingPayment.getCouponId())
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