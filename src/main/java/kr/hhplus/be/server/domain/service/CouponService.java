package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 쿠폰 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 쿠폰 조회, 발급 등의 기능을 제공하며,
 * 동시성 제어를 통해 쿠폰 재고 관리를 합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final TransactionTemplate transactionTemplate;
    private final GetCouponListUseCase getCouponListUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator lockKeyGenerator;
    
    /**
     * 사용자의 쿠폰 히스토리 목록 조회 (캐시 적용)
     * 
     * @param userId 사용자 ID
     * @param limit 조회할 쿠폰 개수
     * @param offset 건너뛸 쿠폰 개수
     * @return 쿠폰 히스토리 목록
     */
    public List<CouponHistory> getCouponList(Long userId, int limit, int offset) {
        log.debug("쿠폰 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        if (!userRepositoryPort.existsById(userId)) {
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        try {
            String cacheKey = lockKeyGenerator.generateCouponListCacheKey(userId, limit, offset);
            return cachePort.getList(cacheKey, () -> {
                List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, limit, offset);
                log.debug("데이터베이스에서 쿠폰 목록 조회: userId={}, count={}", userId, couponHistories.size());
                return couponHistories;
            });
        } catch (Exception e) {
            log.error("쿠폰 목록 조회 중 오류 발생: userId={}, limit={}, offset={}", userId, limit, offset, e);
            return getCouponListUseCase.execute(userId, limit, offset);
        }
    }

    /**
     * 쿠폰 발급
     * 
     * 동시성 제어를 위해 분산 락을 사용하고, TransactionTemplate으로 명시적 트랜잭션 관리합니다.
     * 실행 순서: Lock 획득 → Transaction 시작 → Logic 실행 → Transaction 종료 → Lock 해제
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급된 쿠폰 히스토리
     */
    public CouponHistory issueCoupon(Long couponId, Long userId) {
        String lockKey = lockKeyGenerator.generateCouponKey(couponId);
        
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            CouponHistory result = transactionTemplate.execute(status -> {
                return issueCouponUseCase.execute(couponId, userId);
            });
            
            // 트랜잭션 커밋 후 캐시 무효화
            invalidateUserCouponCache(userId);
            
            return result;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
    
    /**
     * 사용자 쿠폰 캐시 무효화
     * 
     * @param userId 사용자 ID
     */
    private void invalidateUserCouponCache(Long userId) {
        try {
            String cacheKeyPattern = "coupon:list:user_" + userId + "_*";
            cachePort.evictByPattern(cacheKeyPattern);
            
            log.debug("사용자 쿠폰 캐시 무효화: userId={}, pattern={}", userId, cacheKeyPattern);
        } catch (Exception e) {
            log.warn("사용자 쿠폰 캐시 무효화 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}