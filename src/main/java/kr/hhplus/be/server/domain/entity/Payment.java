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
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    private Long orderId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    private Long couponId;

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }
} 