package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepositoryPort extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);
    
    @Modifying
    @Query("UPDATE Payment p SET p.status = :status WHERE p.id = :paymentId")
    int updateStatus(@Param("paymentId") Long paymentId, @Param("status") PaymentStatus status);
} 