package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "orders", 
       indexes = {
           @Index(name = "idx_order_user_id", columnList = "userId"),
           @Index(name = "idx_order_status", columnList = "status"),
           @Index(name = "idx_order_total_amount", columnList = "totalAmount"),
           @Index(name = "idx_order_user_status_created", columnList = "userId, status, createdAt")
       })
public class Order extends BaseEntity {

    @NotNull
    @Positive
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal totalAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private OrderStatus status = OrderStatus.PENDING;

} 