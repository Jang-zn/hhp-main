package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
@Table(name = "coupon_history",
       indexes = {
           @Index(name = "idx_coupon_history_user_id", columnList = "userId"),
           @Index(name = "idx_coupon_history_coupon_id", columnList = "couponId"),
           @Index(name = "idx_coupon_history_status", columnList = "status"),
           @Index(name = "idx_coupon_history_user_status", columnList = "userId, status"),
           @Index(name = "idx_coupon_history_user_coupon", columnList = "userId, couponId")
       })
public class CouponHistory extends BaseEntity {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long couponId;

    @NotNull
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private CouponHistoryStatus status;

    private LocalDateTime usedAt;

    @Positive
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
     * 쿠폰 사용 처리.
     */
    public void useCoupon(Order order) {
        if (!this.status.isUsable()) {
            throw new CouponException.CouponNotUsable();
        }
        
        updateStatus(CouponHistoryStatus.USED);
        this.usedAt = LocalDateTime.now();
        this.usedOrderId = order.getId();
    }

    /**
     * 쿠폰이 사용 가능한지 확인
     */
    public boolean canUse() {
        if (this.status != CouponHistoryStatus.ISSUED) {
            return false;
        }
        
        return true;
    }


    /**
     * 쿠폰 만료 처리
     */
    public void expire() {
        if (this.status == CouponHistoryStatus.ISSUED) {
            updateStatus(CouponHistoryStatus.EXPIRED);
        }
    }
} 