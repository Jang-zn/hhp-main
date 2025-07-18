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
public class Product extends BaseEntity {

    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    private int stock;

    private int reservedStock;

    public void decreaseStock(int quantity) {
        if (this.stock - quantity < 0) {
            throw new RuntimeException("Product stock exceeded");
        }
        this.stock -= quantity;
    }
} 