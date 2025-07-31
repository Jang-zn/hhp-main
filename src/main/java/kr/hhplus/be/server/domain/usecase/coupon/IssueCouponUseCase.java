package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueCouponUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponRepositoryPort couponRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    public CouponHistory execute(Long userId, Long couponId) {
        log.info("쿠폰 발급 요청: userId={}, couponId={}", userId, couponId);
        
        // 입력 값 검증
        validateInputs(userId, couponId);
        
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
        
        // 쿠폰 발급 가능성 검증 (상태 기반)
        coupon.updateStatusIfNeeded(); // 상태 업데이트
        if (!coupon.canIssue()) {
            log.warn("발급 불가능한 쿠폰: couponId={}, status={}", couponId, coupon.getStatus());
            
            // 상태에 따른 구체적인 예외 던지기
            switch (coupon.getStatus()) {
                case EXPIRED:
                    throw new CouponException.Expired();
                case SOLD_OUT:
                    throw new CouponException.OutOfStock();
                case INACTIVE:
                    throw new CouponException.CouponNotYetStarted();
                case DISABLED:
                    throw new CouponException.CouponNotIssuable();
                default:
                    throw new CouponException.CouponNotIssuable();
            }
        }
        
        // 중복 발급 검증
        if (couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)) {
            log.warn("중복 발급 시도: userId={}, couponId={}", userId, couponId);
            throw new CouponException.AlreadyIssued();
        }
        
        // 재고 감소 (내부적으로 상태 업데이트됨)
        coupon.decreaseStock(1);
        Coupon savedCoupon = couponRepositoryPort.save(coupon);
        
        // 쿠폰 발급 이력 저장
        CouponHistory couponHistory = CouponHistory.builder()
                .user(user)
                .coupon(savedCoupon)
                .issuedAt(LocalDateTime.now())
                .status(CouponHistoryStatus.ISSUED)
                .build();
        
        CouponHistory savedHistory = couponHistoryRepositoryPort.save(couponHistory);
        
        log.info("쿠폰 발급 완료: userId={}, couponId={}, couponCode={}", 
                userId, couponId, coupon.getCode());
        
        return savedHistory;
    }
    
    private void validateInputs(Long userId, Long couponId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (couponId == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
        }
    }
    
    // 기존 validateCouponAvailability 메서드는 제거
    // 쿠폰 엔티티의 isIssuable() 메서드로 대체됨
} 