package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueCouponUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponRepositoryPort couponRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final LockingPort lockingPort;
    
    @Transactional
    public CouponHistory execute(Long userId, Long couponId) {
        log.info("쿠폰 발급 요청: userId={}, couponId={}", userId, couponId);
        
        // 입력 값 검증
        validateInputs(userId, couponId);
        
        String lockKey = "coupon-issue-" + couponId;
        if (!lockingPort.acquireLock(lockKey)) {
            log.warn("쿠폰 락 획득 실패: couponId={}", couponId);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 사용자 조회
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("사용자 없음: userId={}", userId);
                        return new UserException.NotFound();
                    });
            
            // 쿠폰 조회
            Coupon coupon = couponRepositoryPort.findById(couponId)
                    .orElseThrow(() -> {
                        log.warn("쿠폰 없음: couponId={}", couponId);
                        return new CouponException.NotFound();
                    });
            
            // 쿠폰 유효성 검증
            validateCouponAvailability(coupon);
            
            // 중복 발급 검증
            if (couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)) {
                log.warn("중복 발급 시도: userId={}, couponId={}", userId, couponId);
                throw new CouponException.AlreadyIssued();
            }
            
            // 재고 감소
            coupon.decreaseStock(1);
            Coupon savedCoupon = couponRepositoryPort.save(coupon);
            
            // 쿠폰 발급 이력 저장
            CouponHistory couponHistory = CouponHistory.builder()
                    .user(user)
                    .coupon(savedCoupon)
                    .issuedAt(LocalDateTime.now())
                    .build();
            
            CouponHistory savedHistory = couponHistoryRepositoryPort.save(couponHistory);
            
            log.info("쿠폰 발급 완료: userId={}, couponId={}, couponCode={}", 
                    userId, couponId, coupon.getCode());
            
            return savedHistory;
            
        } catch (CouponException e) {
            log.error("쿠폰 발급 실패: userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("쿠폰 발급 실패: userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
            throw e;
        } catch (IllegalStateException e) {
            log.error("쿠폰 발급 실패: userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 발급 중 예상치 못한 오류: userId={}, couponId={}", userId, couponId, e);
            throw e;
        } finally {
            lockingPort.releaseLock(lockKey);
            log.debug("쿠폰 락 해제 완료: couponId={}", couponId);
        }
    }
    
    private void validateInputs(Long userId, Long couponId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (couponId == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
        }
    }
    
    private void validateCouponAvailability(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        
        // 쿠폰 시작 시간 검증
        if (now.isBefore(coupon.getStartDate())) {
            throw new IllegalStateException(CouponException.Messages.COUPON_NOT_YET_STARTED);
        }
        
        // 쿠폰 만료 시간 검증
        if (now.isAfter(coupon.getEndDate())) {
            throw new CouponException.Expired();
        }
        
        // 쿠폰 재고 검증
        if (coupon.getIssuedCount() >= coupon.getMaxIssuance()) {
            throw new CouponException.OutOfStock();
        }
    }
} 