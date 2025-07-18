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
    
    private final Map<String, Payment> payments = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(payments.get(id));
    }
    
    @Override
    public List<Payment> findByOrderId(String orderId) {
        // TODO: 주문별 결제 조회 로직 구현
        return new ArrayList<>();
    }
    
    @Override
    public Payment save(Payment payment) {
        payments.put(payment.getId(), payment);
        return payment;
    }
    
    @Override
    public Payment updateStatus(String paymentId, String status) {
        Payment payment = payments.get(paymentId);
        if (payment != null) {
            // TODO: 실제 상태 업데이트 로직 구현
        }
        return payment;
    }
} 