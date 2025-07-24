package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
// @Entity
// @Table(name = "orders")
public class Order extends BaseEntity {

    // @ManyToOne
    // @JoinColumn(name = "user_id")
    private User user;

    // @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    // @Enumerated(EnumType.STRING)
    // @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    // @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    // @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();
} 