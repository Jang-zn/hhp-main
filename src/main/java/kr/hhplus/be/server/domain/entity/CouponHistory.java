package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "coupon_history")
public class CouponHistory extends BaseEntity {

    private Long userId;

    private Long couponId;

    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponHistoryStatus status;

    private LocalDateTime usedAt;

    private Long usedOrderId;

    /**
     * 쿠폰 히스토리 상태를 업데이트합니다.
     */
    public void updateStatus(CouponHistoryStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new CouponException.InvalidHistoryStatusTransition(
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
        
        if (newStatus == CouponHistoryStatus.USED) {
            this.usedAt = LocalDateTime.now();
        }
    }

    /**
     * 쿠폰을 사용 처리합니다.
     */
    public void useCoupon(Order order) {
        if (!this.status.isUsable()) {
            throw new CouponException.CouponNotUsable();
        }
        
        // 쿠폰 만료 여부 확인은 서비스 층에서 처리
        
        updateStatus(CouponHistoryStatus.USED);
        this.usedAt = LocalDateTime.now();
        this.usedOrderId = order.getId();
    }

    /**
     * 쿠폰이 사용 가능한지 확인합니다 (상태 변경 없이 순수 조회).
     */
    public boolean canUse() {
        // 현재 상태가 발급됨인지 확인
        if (this.status != CouponHistoryStatus.ISSUED) {
            return false;
        }
        
        // 쿠폰 만료 여부 확인은 서비스 층에서 처리
        return true;
    }

    /**
     * 만료된 쿠폰의 상태를 업데이트합니다.
     */
    public void updateStatusIfExpired() {
        // 만료 처리는 서비스 층에서 처리
    }

    /**
     * 만료 처리합니다.
     */
    public void expire() {
        if (this.status == CouponHistoryStatus.ISSUED) {
            updateStatus(CouponHistoryStatus.EXPIRED);
        }
    }
} 