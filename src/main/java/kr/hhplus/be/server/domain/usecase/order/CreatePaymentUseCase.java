package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatePaymentUseCase {
    
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
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
        
        // Write-Through: 결제 정보를 캐시에 저장
        try {
            String cacheKey = keyGenerator.generatePaymentCacheKey(savedPayment.getId());
            cachePort.put(cacheKey, savedPayment, CacheTTL.PAYMENT_DETAIL.getSeconds());
            log.debug("결제 정보 캐시 저장 완료: paymentId={}", savedPayment.getId());
        } catch (Exception e) {
            log.warn("결제 캐시 처리 실패: paymentId={}, orderId={}", savedPayment.getId(), orderId, e);
            // 캐시 오류는 비즈니스 로직에 영향을 주지 않음
        }
        
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