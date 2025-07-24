package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
// @Entity
public class OrderItem extends BaseEntity {

    // @ManyToOne
    // @JoinColumn(name = "order_id")
    private Order order;

    // @ManyToOne
    // @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    // @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
} 