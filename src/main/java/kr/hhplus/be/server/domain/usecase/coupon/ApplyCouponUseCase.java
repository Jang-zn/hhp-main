package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.exception.CouponException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplyCouponUseCase {
    
    private final CouponRepositoryPort couponRepositoryPort;
    
    public BigDecimal execute(BigDecimal originalAmount, Long couponId) {
        log.debug("쿠폰 적용: originalAmount={}, couponId={}", originalAmount, couponId);
        
        // 쿠폰이 없는 경우 원래 금액 반환
        if (couponId == null) {
            log.debug("쿠폰 없음, 원래 금액 반환: {}", originalAmount);
            return originalAmount;
        }
        
        // 원래 금액 검증
        validateAmount(originalAmount);
        
        // 쿠폰 조회
        Coupon coupon = couponRepositoryPort.findById(couponId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 쿠폰: couponId={}", couponId);
                    return new CouponException.NotFound();
                });
                
        // 할인 금액 계산
        BigDecimal discountedAmount = originalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
        
        log.info("쿠폰 적용 완료: originalAmount={}, discountRate={}, discountedAmount={}", 
                originalAmount, coupon.getDiscountRate(), discountedAmount);
        
        return discountedAmount;
    }
    
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Original amount cannot be null");
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Original amount must be positive");
        }
    }
}