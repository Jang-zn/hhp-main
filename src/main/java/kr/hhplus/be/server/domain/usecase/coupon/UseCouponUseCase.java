package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 사용 UseCase
 * 주문 시 보유한 쿠폰을 사용 처리합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UseCouponUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final LockingPort lockingPort;
    
    
    public void execute(Long userId, List<Long> couponHistoryIds, Order order) {
        // 입력 값 검증
        validateInputs(userId, couponHistoryIds, order);
        
        log.info("쿠폰 사용 요청: userId={}, couponHistoryIds={}, orderId={}", 
                userId, couponHistoryIds, order.getId());
        
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> {
                    log.warn("사용자 없음: userId={}", userId);
                    return new UserException.NotFound();
                });
        
        // 각 쿠폰에 대해 순차적으로 처리 (데드락 방지를 위해 ID 순으로 정렬)
        couponHistoryIds.stream()
                .sorted()
                .forEach(couponHistoryId -> useSingleCoupon(user, couponHistoryId, order));
        
        log.info("쿠폰 사용 완료: userId={}, couponHistoryIds={}, orderId={}", 
                userId, couponHistoryIds, order.getId());
    }
    
    private void useSingleCoupon(User user, Long couponHistoryId, Order order) {
        String lockKey = "coupon-use-" + couponHistoryId;
        
        if (!lockingPort.acquireLock(lockKey)) {
            log.warn("쿠폰 사용 락 획득 실패: couponHistoryId={}", couponHistoryId);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 쿠폰 히스토리 조회
            CouponHistory couponHistory = couponHistoryRepositoryPort.findById(couponHistoryId)
                    .orElseThrow(() -> {
                        log.warn("쿠폰 히스토리 없음: couponHistoryId={}", couponHistoryId);
                        return new CouponException.NotFound();
                    });
            
            // 소유권 검증
            if (!couponHistory.getUserId().equals(user.getId())) {
                log.warn("쿠폰 소유권 없음: userId={}, couponHistoryId={}, ownerId={}", 
                        user.getId(), couponHistoryId, couponHistory.getUserId());
                throw new CouponException.NotFound();
            }
            
            // 쿠폰 사용 처리
            couponHistory.useCoupon(order);
            couponHistoryRepositoryPort.save(couponHistory);
            
            log.info("쿠폰 사용 처리 완료: couponHistoryId={}, orderId={}", 
                    couponHistoryId, order.getId());
            
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
    
    private void validateInputs(Long userId, List<Long> couponHistoryIds, Order order) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (couponHistoryIds == null || couponHistoryIds.isEmpty()) {
            throw new IllegalArgumentException("Coupon history IDs cannot be null or empty");
        }
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        // 중복된 쿠폰 ID 검증
        if (couponHistoryIds.size() != couponHistoryIds.stream().distinct().count()) {
            throw new IllegalArgumentException("Duplicate coupon history IDs found");
        }
    }
}