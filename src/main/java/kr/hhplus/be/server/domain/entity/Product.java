package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
public class Product extends BaseEntity {

    @Id
    private String id;

    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    private int stock;

    private int reservedStock;
} 