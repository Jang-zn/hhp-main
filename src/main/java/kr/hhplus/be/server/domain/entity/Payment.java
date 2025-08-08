package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.enums.PaymentStatus;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "payment", 
       indexes = {
           @Index(name = "idx_payment_order_id", columnList = "orderId"),
           @Index(name = "idx_payment_user_id", columnList = "userId"),
           @Index(name = "idx_payment_coupon_id", columnList = "couponId")
       })
public class Payment extends BaseEntity {

    /**
     * 주문 ID (외래키: orders.id)
     * 데이터베이스 레벨에서 외래키 제약 조건 적용 필요
     */
    @Column(nullable = false)
    @NotNull
    @Positive
    private Long orderId;

    /**
     * 사용자 ID (외래키: users.id)
     * 데이터베이스 레벨에서 외래키 제약 조건 적용 필요
     */
    @Column(nullable = false)
    @NotNull
    @Positive
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    /**
     * 쿠폰 ID (외래키: coupon.id, 선택적)
     * 데이터베이스 레벨에서 외래키 제약 조건 적용 필요
     */
    @Column(nullable = true)  // 명시적으로 nullable 설정 (선택적 필드)
    @Positive
    private Long couponId;

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }
} 