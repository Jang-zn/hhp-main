package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@Repository
@Profile({"local", "test", "dev", "prod"})
@RequiredArgsConstructor
public class PaymentJpaRepository implements PaymentRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public Optional<Payment> findById(Long id) {
        try {
            Payment payment = entityManager.find(Payment.class, id);
            return Optional.ofNullable(payment);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return entityManager.createQuery(
            "SELECT p FROM Payment p WHERE p.order.id = :orderId", Payment.class)
            .setParameter("orderId", orderId)
            .getResultList();
    }

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            entityManager.persist(payment);
            return payment;
        } else {
            return entityManager.merge(payment);
        }
    }

    @Override
    public Payment updateStatus(Long paymentId, PaymentStatus status) {
        Payment payment = entityManager.find(Payment.class, paymentId);
        if (payment != null) {
            payment.changeStatus(status);
            return entityManager.merge(payment);
        }
        return null;
    }
}