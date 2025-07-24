package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
public class Coupon extends BaseEntity {

    private String code;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRate;

    private int maxIssuance;

    private int issuedCount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    public void decreaseStock(int quantity) {
        if (this.issuedCount + quantity > this.maxIssuance) {
            throw new RuntimeException("Coupon stock exceeded");
        }
        this.issuedCount += quantity;
    }
} 