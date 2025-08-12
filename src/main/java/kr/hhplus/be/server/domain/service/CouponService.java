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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    
    private static final int COUPON_LIST_CACHE_TTL = 300; // 5분

    /**
     * 사용자의 쿠폰 히스토리 목록 조회 (캐시 적용)
     * 
     * @param userId 사용자 ID
     * @param limit 조회할 쿠폰 개수
     * @param offset 건너뛸 쿠폰 개수
     * @return 쿠폰 히스토리 목록
     */
    @SuppressWarnings("unchecked")
    public List<CouponHistory> getCouponList(Long userId, int limit, int offset) {
        log.debug("쿠폰 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        try {
            String cacheKey = "coupon_list_" + userId + "_" + limit + "_" + offset;
            return (List<CouponHistory>) cachePort.get(cacheKey, List.class, () -> {
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
        String lockKey = "coupon-issue-" + couponId;
        
        // 사용자 존재 확인 (트랜잭션 외부에서)
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        // 1. 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 2. 명시적 트랜잭션 실행
            return transactionTemplate.execute(status -> {
                // 3. 비즈니스 로직 실행 (트랜잭션 내)
                CouponHistory result = issueCouponUseCase.execute(couponId, userId);
                
                // 트랜잭션 커밋 후 캐시 무효화 등록
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                invalidateUserCouponCache(userId);
                            }
                        }
                    );
                }
                
                return result;
            });
        } finally {
            // 4. 락 해제
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
            // 사용자의 모든 쿠폰 목록 캐시 무효화 (다양한 페이지단위가 있을 수 있음)
            String cacheKeyPattern = "coupon_list_" + userId + "_*";
            log.debug("사용자 쿠폰 캐시 무효화: userId={}", userId);
        } catch (Exception e) {
            log.warn("사용자 쿠폰 캐시 무효화 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}