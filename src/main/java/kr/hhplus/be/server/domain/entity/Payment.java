package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.enums.PaymentStatus;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
// @Entity
public class Payment extends BaseEntity {

    // @ManyToOne
    // @JoinColumn(name = "order_id")
    private Order order;

    // @ManyToOne
    // @JoinColumn(name = "user_id")
    private User user;

    // @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // @ManyToOne
    // @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }
} 