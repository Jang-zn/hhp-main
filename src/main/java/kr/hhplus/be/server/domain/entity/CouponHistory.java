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
// @Entity
public class CouponHistory extends BaseEntity {

    // @ManyToOne
    // @JoinColumn(name = "user_id")
    private User user;

    // @ManyToOne
    // @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    private LocalDateTime issuedAt;

    // @Enumerated(EnumType.STRING)
    // @Column(nullable = false)
    private CouponHistoryStatus status;

    private LocalDateTime usedAt;

    // @ManyToOne
    // @JoinColumn(name = "order_id")
    private Order usedOrder;

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
        
        // 쿠폰 만료 여부 확인
        if (LocalDateTime.now().isAfter(this.coupon.getEndDate())) {
            throw new CouponException.Expired();
        }
        
        updateStatus(CouponHistoryStatus.USED);
        this.usedAt = LocalDateTime.now();
        this.usedOrder = order;
    }

    /**
     * 쿠폰이 사용 가능한지 확인합니다 (상태 변경 없이 순수 조회).
     */
    public boolean canUse() {
        // 현재 상태가 발급됨인지 확인
        if (this.status != CouponHistoryStatus.ISSUED) {
            return false;
        }
        
        // 쿠폰 만료 여부 확인
        return !LocalDateTime.now().isAfter(this.coupon.getEndDate());
    }

    /**
     * 만료된 쿠폰의 상태를 업데이트합니다.
     */
    public void updateStatusIfExpired() {
        // 현재 상태가 발급됨이고 만료된 경우에만 업데이트
        if (this.status == CouponHistoryStatus.ISSUED && 
            LocalDateTime.now().isAfter(this.coupon.getEndDate())) {
            updateStatus(CouponHistoryStatus.EXPIRED);
        }
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