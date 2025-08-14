package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
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
            throw new ProductException.OutOfStock();
        }
        this.stock -= quantity;
    }
    
    /**
     * 실제 재고 차감 없이 예약만으로 재고 관리
     * @param quantity 예약할 수량
     */
    public void reserveStock(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new ProductException.OutOfStock();
        }
        
        this.reservedStock += quantity;
    }
    
    /**
     * @param quantity 확정할 수량
     */
    public void confirmReservation(int quantity) {
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
     * @param quantity 취소할 수량
     */
    public void cancelReservation(int quantity) {
        if (this.reservedStock < quantity) {
            throw new ProductException.InvalidReservation("예약된 수량보다 많은 수량을 취소할 수 없습니다");
        }
        
        this.reservedStock -= quantity;
    }
    
    /**
     * 보상 트랜잭션에서 확정된 재고를 되돌려야 함
     * @param quantity 복원할 수량
     */
    public void restoreReservation(int quantity) {
        
        this.stock += quantity;
        this.reservedStock += quantity;
    }
    
    /**
     * 전체 재고 - 예약된 재고로 계산
     * @param quantity 확인할 수량
     */
    public boolean hasAvailableStock(int quantity) {
        return (this.stock - this.reservedStock) >= quantity;
    }
    
} 