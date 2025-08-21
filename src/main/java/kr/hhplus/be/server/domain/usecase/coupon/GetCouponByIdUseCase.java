package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 ID로 쿠폰 정보를 조회하는 UseCase
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetCouponByIdUseCase {
    
    private final CouponRepositoryPort couponRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 쿠폰 ID로 쿠폰 정보 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 정보
     * @throws CouponException.NotFound 쿠폰을 찾을 수 없는 경우
     */
    public Coupon execute(Long couponId) {
        log.debug("쿠폰 조회 요청: couponId={}", couponId);
        
        if (couponId == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
        }
        
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
}