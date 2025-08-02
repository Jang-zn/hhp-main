package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;

    private Long productId;

    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
} 