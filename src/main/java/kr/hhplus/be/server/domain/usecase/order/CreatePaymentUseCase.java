package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.User;
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
    
    public Payment execute(Order order, User user, BigDecimal amount) {
        log.debug("결제 생성: orderId={}, userId={}, amount={}", order.getId(), user.getId(), amount);
        
        // 입력 검증
        validateInputs(order, user, amount);
        
        // 결제 생성
        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .amount(amount)
                .status(PaymentStatus.PAID)
                .build();

        Payment savedPayment = paymentRepositoryPort.save(payment);
        
        log.info("결제 생성 완료: paymentId={}, orderId={}, userId={}, amount={}", 
                savedPayment.getId(), order.getId(), user.getId(), amount);
        
        return savedPayment;
    }
    
    private void validateInputs(Order order, User user, BigDecimal amount) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}