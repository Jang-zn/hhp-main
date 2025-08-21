package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 쿠폰 처리 배치 UseCase
 * 주기적으로 실행되어 만료된 쿠폰들의 상태를 업데이트합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpireCouponsUseCase {
    
    private final CouponRepositoryPort couponRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        log.info("만료 쿠폰 처리 시작: {}", now);
        
        try {
            // 1. 만료된 쿠폰들의 상태 업데이트
            int expiredCouponsCount = expireCoupons(now);
            
            // 2. 만료된 쿠폰 히스토리들의 상태 업데이트
            int expiredHistoriesCount = expireCouponHistories(now);
            
            // 3. 대량 캐시 무효화 (배치 처리 특성상 모든 쿠폰 관련 캐시 클리어)
            if (expiredCouponsCount > 0 || expiredHistoriesCount > 0) {
                clearExpiredCouponsCache();
            }
            
            log.info("만료 쿠폰 처리 완료: 쿠폰 {}개, 히스토리 {}개", 
                    expiredCouponsCount, expiredHistoriesCount);
            
        } catch (Exception e) {
            log.error("만료 쿠폰 처리 중 오류 발생", e);
            throw e;
        }
    }
    
    private int expireCoupons(LocalDateTime now) {
        // 만료되었지만 아직 EXPIRED 상태가 아닌 쿠폰들 조회
        List<CouponStatus> excludeStatuses = List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED);
        List<Coupon> expiredCoupons = excludeStatuses.isEmpty() 
                ? couponRepositoryPort.findExpiredCoupons(now)
                : couponRepositoryPort.findExpiredCouponsNotInStatus(now, excludeStatuses);
        
        if (expiredCoupons.isEmpty()) {
            log.debug("만료 처리할 쿠폰이 없습니다");
            return 0;
        }
        
        // 각 쿠폰의 상태를 EXPIRED로 업데이트
        for (Coupon coupon : expiredCoupons) {
            try {
                coupon.updateStatus(CouponStatus.EXPIRED);
                couponRepositoryPort.save(coupon);
                log.debug("쿠폰 만료 처리: couponId={}, code={}", 
                        coupon.getId(), coupon.getCode());
            } catch (Exception e) {
                log.warn("쿠폰 만료 처리 실패: couponId={}, error={}", 
                        coupon.getId(), e.getMessage());
            }
        }
        
        return expiredCoupons.size();
    }
    
    private int expireCouponHistories(LocalDateTime now) {
        // 만료되었지만 아직 EXPIRED 상태가 아닌 쿠폰 히스토리들 조회
        List<CouponHistory> expiredHistories = couponHistoryRepositoryPort
                .findExpiredHistoriesInStatus(now, CouponHistoryStatus.ISSUED);
        
        if (expiredHistories.isEmpty()) {
            log.debug("만료 처리할 쿠폰 히스토리가 없습니다");
            return 0;
        }
        
        // 각 쿠폰 히스토리의 상태를 EXPIRED로 업데이트
        for (CouponHistory history : expiredHistories) {
            try {
                history.expire();
                couponHistoryRepositoryPort.save(history);
                log.debug("쿠폰 히스토리 만료 처리: historyId={}, couponId={}", 
                        history.getId(), history.getCouponId());
            } catch (Exception e) {
                log.warn("쿠폰 히스토리 만료 처리 실패: historyId={}, error={}", 
                        history.getId(), e.getMessage());
            }
        }
        
        return expiredHistories.size();
    }
    
    private void clearExpiredCouponsCache() {
        try {
            // 쿠폰 목록 캐시 패턴 무효화 (모든 사용자의 쿠폰 목록)
            String couponListPattern = keyGenerator.generateCouponListCachePattern("*");
            cachePort.evictByPattern(couponListPattern);
            
            log.info("만료 쿠폰 대량 캐시 무효화 완료");
        } catch (Exception e) {
            log.warn("만료 쿠폰 캐시 무효화 실패", e);
            // 캐시 오류는 배치 작업에 영향을 주지 않음
        }
    }
}