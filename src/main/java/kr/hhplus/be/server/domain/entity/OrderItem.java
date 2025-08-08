package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "order_item",
       indexes = {
           @Index(name = "idx_order_item_order_id", columnList = "orderId"),
           @Index(name = "idx_order_item_product_id", columnList = "productId")
       })
public class OrderItem extends BaseEntity {

    /**
     * 주문 ID (외래키: orders.id)
     * 데이터베이스 레벨에서 외래키 제약 조건 적용 필요
     */
    @Column(nullable = false)
    @NotNull
    @Positive
    private Long orderId;

    /**
     * 상품 ID (외래키: product.id)
     * 데이터베이스 레벨에서 외래키 제약 조건 적용 필요
     */
    @Column(nullable = false)
    @NotNull
    @Positive
    private Long productId;

    @Column(nullable = false)
    @Positive
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal price;
    
    /**
     * OrderItem에 orderId를 설정합니다.
     * 새로운 객체를 생성하지 않고 기존 인스턴스를 업데이트하여 성능을 향상시킵니다.
     * 
     * @param orderId 설정할 주문 ID
     * @return orderId가 설정된 현재 OrderItem 인스턴스
     */
    public OrderItem withOrderId(@NotNull @Positive Long orderId) {
        this.orderId = orderId;
        return this;
    }
} 