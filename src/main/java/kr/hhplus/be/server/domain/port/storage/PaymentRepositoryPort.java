package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    Optional<Payment> findById(Long id);
    List<Payment> findByOrderId(Long orderId);
    Payment save(Payment payment);
    Payment updateStatus(Long paymentId, PaymentStatus status);
} 