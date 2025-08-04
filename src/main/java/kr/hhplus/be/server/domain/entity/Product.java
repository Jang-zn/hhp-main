package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.exception.ProductException;

import java.math.BigDecimal;

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
    
    /**
     * 재고를 예약합니다. 실제 재고는 차감하지 않고 예약 재고만 증가시킵니다.
     */
    public void reserveStock(int quantity) {
        validateQuantity(quantity);
        
        if (!hasAvailableStock(quantity)) {
            throw new ProductException.OutOfStock();
        }
        
        this.reservedStock += quantity;
    }
    
    /**
     * 예약된 재고를 확정합니다. 실제 재고를 차감하고 예약 재고를 감소시킵니다.
     */
    public void confirmReservation(int quantity) {
        validateQuantity(quantity);
        
        if (this.reservedStock < quantity) {
            throw new ProductException.InvalidReservation("Cannot confirm more than reserved quantity");
        }
        
        if (this.stock < quantity) {
            throw new ProductException.InvalidReservation("Cannot confirm reservation due to insufficient actual stock");
        }
        
        this.stock -= quantity;
        this.reservedStock -= quantity;
    }
    
    /**
     * 예약된 재고를 취소합니다. 예약 재고만 감소시킵니다.
     */
    public void cancelReservation(int quantity) {
        validateQuantity(quantity);
        
        if (this.reservedStock < quantity) {
            throw new ProductException.InvalidReservation("Cannot cancel more than reserved quantity");
        }
        
        this.reservedStock -= quantity;
    }
    
    /**
     * 확정된 재고를 다시 예약 상태로 복원합니다. (보상 처리용)
     * 실제 재고를 증가시키고 예약 재고도 증가시킵니다.
     */
    public void restoreReservation(int quantity) {
        validateQuantity(quantity);
        
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
    
    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
} 