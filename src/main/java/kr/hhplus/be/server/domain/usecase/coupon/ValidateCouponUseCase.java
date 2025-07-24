package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.exception.CouponException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateCouponUseCase {
    
    private final CouponRepositoryPort couponRepositoryPort;
    
    @Transactional(readOnly = true)
    public void execute(List<Long> couponIds) {
        log.debug("쿠폰 유효성 검증 요청: couponIds={}", couponIds);
        
        if (couponIds == null || couponIds.isEmpty()) {
            return; // 쿠폰이 없으면 검증 스킵
        }
        
        for (Long couponId : couponIds) {
            if (couponId == null) {
                throw new IllegalArgumentException("Coupon ID cannot be null");
            }
            
            Coupon coupon = couponRepositoryPort.findById(couponId)
                    .orElseThrow(() -> {
                        log.warn("존재하지 않는 쿠폰: couponId={}", couponId);
                        return new CouponException.NotFound();
                    });
            
            log.debug("쿠폰 유효성 검증 완료: couponId={}, code={}", couponId, coupon.getCode());
        }
    }
}