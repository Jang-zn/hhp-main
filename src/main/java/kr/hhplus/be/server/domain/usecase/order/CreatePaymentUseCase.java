package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatePaymentUseCase {
    
    private final PaymentRepositoryPort paymentRepositoryPort;
    
    public Payment execute(Long orderId, Long userId, BigDecimal amount) {
        log.debug("결제 생성: orderId={}, userId={}, amount={}", orderId, userId, amount);
        
        // 입력 검증
        validateInputs(orderId, userId, amount);
        
        // 결제 생성
        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status(PaymentStatus.PAID)
                .build();

        Payment savedPayment = paymentRepositoryPort.save(payment);
        
        log.info("결제 생성 완료: paymentId={}, orderId={}, userId={}, amount={}", 
                savedPayment.getId(), orderId, userId, amount);
        
        return savedPayment;
    }
    
    private void validateInputs(Long orderId, Long userId, BigDecimal amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}