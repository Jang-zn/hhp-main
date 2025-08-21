package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
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
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    public BigDecimal execute(BigDecimal originalAmount, Long couponId) {
        log.debug("쿠폰 적용: originalAmount={}, couponId={}", originalAmount, couponId);
        
        // 쿠폰이 없는 경우 원래 금액 반환
        if (couponId == null) {
            log.debug("쿠폰 없음, 원래 금액 반환: {}", originalAmount);
            return originalAmount;
        }
        
        // 원래 금액 검증
        validateAmount(originalAmount);
        
        // 쿠폰 조회 (Cache-Aside 패턴)
        Coupon coupon = getCouponWithCache(couponId);
                
        // 할인 금액 계산
        BigDecimal discountedAmount = originalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
        
        log.info("쿠폰 적용 완료: originalAmount={}, discountRate={}, discountedAmount={}", 
                originalAmount, coupon.getDiscountRate(), discountedAmount);
        
        return discountedAmount;
    }
    
    private Coupon getCouponWithCache(Long couponId) {
        try {
            String cacheKey = keyGenerator.generateCouponCacheKey(couponId);
            
            // 캐시에서 조회 시도
            Coupon cachedCoupon = cachePort.get(cacheKey, Coupon.class);
            if (cachedCoupon != null) {
                log.debug("캐시에서 쿠폰 조회 성공: couponId={}", couponId);
                return cachedCoupon;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            Coupon coupon = couponRepositoryPort.findById(couponId)
                    .orElseThrow(() -> {
                        log.warn("존재하지 않는 쿠폰: couponId={}", couponId);
                        return new CouponException.NotFound();
                    });
            
            // 캐시에 저장
            cachePort.put(cacheKey, coupon, CacheTTL.USER_COUPON_LIST.getSeconds());
            log.debug("쿠폰 캐시 저장 완료: couponId={}", couponId);
            
            return coupon;
        } catch (CouponException e) {
            throw e;
        } catch (Exception e) {
            log.warn("쿠폰 캐시 처리 실패, DB에서 직접 조회: couponId={}", couponId, e);
            // 캐시 오류 시 직접 DB에서 조회
            return couponRepositoryPort.findById(couponId)
                    .orElseThrow(() -> new CouponException.NotFound());
        }
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