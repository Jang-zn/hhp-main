package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponByIdUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.enums.CacheTTL;
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
    private final GetCouponByIdUseCase getCouponByIdUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
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
            throw new UserException.NotFound();
        }
        
        try {
            String cacheKey = keyGenerator.generateCouponListCacheKey(userId, limit, offset);
            
            // 캐시에서 조회 시도
            List<CouponHistory> cachedCoupons = cachePort.getList(cacheKey);
            
            if (cachedCoupons != null) {
                log.debug("캐시에서 쿠폰 목록 조회 성공: userId={}, count={}", userId, cachedCoupons.size());
                return cachedCoupons;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            List<CouponHistory> couponHistories = getCouponListUseCase.execute(userId, limit, offset);
            log.debug("데이터베이스에서 쿠폰 목록 조회: userId={}, count={}", userId, couponHistories.size());
            
            // TTL과 함께 캐시에 저장
            cachePort.put(cacheKey, couponHistories, CacheTTL.USER_COUPON_LIST.getSeconds());
            
            return couponHistories;
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
        String lockKey = keyGenerator.generateCouponKey(couponId);
        
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            CouponHistory result = transactionTemplate.execute(status -> {
                return issueCouponUseCase.execute(userId, couponId);
            });
            
            // 트랜잭션 커밋 후 캐시 무효화
            String cacheKeyPattern = keyGenerator.generateCouponListCachePattern(userId);
            cachePort.evictByPattern(cacheKeyPattern);
            
            return result;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
    
    /**
     * 쿠폰 ID로 쿠폰 정보 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 정보
     * @throws CouponException.NotFound 쿠폰을 찾을 수 없는 경우
     */
    public Coupon getCouponById(Long couponId) {
        return getCouponByIdUseCase.execute(couponId);
    }
}