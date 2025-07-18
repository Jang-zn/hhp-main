package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    Optional<Payment> findById(String id);
    List<Payment> findByOrderId(String orderId);
    Payment save(Payment payment);
    Payment updateStatus(String paymentId, String status);
} 