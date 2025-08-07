package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.exception.ProductException;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;
import jakarta.validation.constraints.Min;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "product",
       indexes = {
           @Index(name = "idx_product_stock", columnList = "stock"),
           @Index(name = "idx_product_reserved", columnList = "reservedStock"),
           @Index(name = "idx_product_price", columnList = "price"),
           @Index(name = "idx_product_name", columnList = "name")
       })
@Check(constraints = "stock >= 0 AND reserved_stock >= 0 AND reserved_stock <= stock")
public class Product extends BaseEntity {

    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Min(value = 0)
    private int stock;

    @Column(name = "reserved_stock", nullable = false)
    @Min(value = 0)
    private int reservedStock;

    public void decreaseStock(int quantity) {
        if (this.stock - quantity < 0) {
            throw new RuntimeException("상품 재고가 부족합니다");
        }
        this.stock -= quantity;
    }
    
    /**
     * 재고를 예약합니다. 실제 재고는 차감하지 않고 예약 재고만 증가시킵니다.
     * DB @Check 제약조건으로 추가 무결성 보장:
     * - stock >= 0: 재고는 음수가 될 수 없음
     * - reserved_stock >= 0: 예약 재고는 음수가 될 수 없음  
     * - reserved_stock <= stock: 예약 재고는 실제 재고를 초과할 수 없음
     */
    public void reserveStock(@Positive int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        
        if (!hasAvailableStock(quantity)) {
            throw new ProductException.OutOfStock();
        }
        
        this.reservedStock += quantity;
        // DB @Check 제약 조건이 추가 검증 수행하여 데이터 무결성 보장
    }
    
    /**
     * 예약된 재고를 확정합니다. 실제 재고를 차감하고 예약 재고를 감소시킵니다.
     */
    public void confirmReservation(@Positive int quantity) {
        if (this.reservedStock < quantity) {
            throw new ProductException.InvalidReservation("예약된 수량보다 많은 수량을 확정할 수 없습니다");
        }
        
        if (this.stock < quantity) {
            throw new ProductException.InvalidReservation("실제 재고 부족으로 예약을 확정할 수 없습니다");
        }
        
        this.stock -= quantity;
        this.reservedStock -= quantity;
    }
    
    /**
     * 예약된 재고를 취소합니다. 예약 재고만 감소시킵니다.
     */
    public void cancelReservation(@Positive int quantity) {
        if (this.reservedStock < quantity) {
            throw new ProductException.InvalidReservation("예약된 수량보다 많은 수량을 취소할 수 없습니다");
        }
        
        this.reservedStock -= quantity;
    }
    
    /**
     * 확정된 재고를 다시 예약 상태로 복원합니다. (보상 처리용)
     * 실제 재고를 증가시키고 예약 재고도 증가시킵니다.
     */
    public void restoreReservation(@Positive int quantity) {
        
        this.stock += quantity;
        this.reservedStock += quantity;
    }
    
    /**
     * 이용 가능한 재고가 있는지 확인합니다.
     * 이용 가능한 재고 = 전체 재고 - 예약된 재고
     */
    public boolean hasAvailableStock(int quantity) {
        return (this.stock - this.reservedStock) >= quantity;
    }
    
} 