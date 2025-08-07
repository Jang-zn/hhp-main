package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "coupon",
       indexes = {
           @Index(name = "idx_coupon_status", columnList = "status"),
           @Index(name = "idx_coupon_product_id", columnList = "productId"),
           @Index(name = "idx_coupon_code", columnList = "code", unique = true),
           @Index(name = "idx_coupon_end_date", columnList = "endDate"),
           @Index(name = "idx_coupon_end_date_status", columnList = "endDate, status")
       })
@Check(constraints = "issued_count <= max_issuance AND issued_count >= 0 AND max_issuance >= 0")
public class Coupon extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    private String code;

    @Column(nullable = false, precision = 5, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "1.00")
    private BigDecimal discountRate;

    @Column(name = "max_issuance", nullable = false)
    @PositiveOrZero
    private int maxIssuance;

    @Column(name = "issued_count", nullable = false)
    @PositiveOrZero
    private int issuedCount = 0;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private CouponStatus status;

    private Long productId;

    /**
     * 쿠폰 상태를 업데이트합니다.
     * 상태 전이 규칙을 검증한 후 업데이트를 수행합니다.
     */
    public void updateStatus(CouponStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new CouponException.InvalidStatusTransition(
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
    }

    /**
     * 현재 시간을 기준으로 쿠폰 상태를 자동으로 계산하고 업데이트합니다.
     */
    public void updateStatusBasedOnConditions() {
        LocalDateTime now = LocalDateTime.now();
        CouponStatus newStatus = calculateStatus(now);
        
        if (this.status != newStatus && this.status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        }
    }

    /**
     * 조건에 따른 쿠폰 상태를 계산합니다.
     */
    private CouponStatus calculateStatus(LocalDateTime now) {
        // 비활성화된 쿠폰은 그대로 유지
        if (this.status == CouponStatus.DISABLED) {
            return CouponStatus.DISABLED;
        }
        
        // 만료 시간 체크
        if (now.isAfter(this.endDate)) {
            return CouponStatus.EXPIRED;
        }
        
        // 재고 소진 체크
        if (this.issuedCount >= this.maxIssuance) {
            return CouponStatus.SOLD_OUT;
        }
        
        // 시작 시간 체크
        if (now.isBefore(this.startDate)) {
            return CouponStatus.INACTIVE;
        }
        
        return CouponStatus.ACTIVE;
    }

    /**
     * 쿠폰을 발급합니다.
     * DB @Check 제약조건으로 추가 무결성 보장:
     * - issued_count <= max_issuance: 발급 수량이 최대 한도를 초과할 수 없음
     * - issued_count >= 0: 발급 수량이 음수가 될 수 없음
     * - max_issuance >= 0: 최대 발급량이 음수가 될 수 없음
     */
    public void issue() {
        if (this.issuedCount >= this.maxIssuance) {
            throw new CouponException.CouponStockExceeded();
        }
        
        this.issuedCount += 1;
        // DB @Check 제약 조건이 추가 검증 수행하여 데이터 무결성 보장
        
        // 재고 소진 시 상태 업데이트
        if (this.issuedCount >= this.maxIssuance) {
            updateStatus(CouponStatus.SOLD_OUT);
        }
    }


    /**
     * 쿠폰 발급이 가능한지 확인합니다 (상태 변경 없이 순수 조회).
     */
    public boolean canIssue() {
        LocalDateTime now = LocalDateTime.now();
        CouponStatus currentStatus = calculateStatus(now);
        return currentStatus.isIssuable();
    }

    /**
     * 쿠폰 상태를 조건에 따라 업데이트합니다.
     */
    public void updateStatusIfNeeded() {
        updateStatusBasedOnConditions();
    }

    /**
     * 강제로 쿠폰을 비활성화합니다.
     */
    public void disable() {
        updateStatus(CouponStatus.DISABLED);
    }

    /**
     * 비활성화된 쿠폰을 다시 활성화합니다.
     */
    public void activate() {
        if (this.status == CouponStatus.DISABLED) {
            updateStatusBasedOnConditions();
        }
    }
} 