package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;

import java.math.BigDecimal;

@Schema(description = "상품 수정 요청")
public class UpdateProductRequest implements DocumentedDto {
    
    @Schema(description = "상품명", example = "스마트폰 프로", required = false)
    @Size(max = 100, message = "상품명은 100자를 초과할 수 없습니다.")
    private String name;
    
    @Schema(description = "상품 가격", example = "399000", required = false)
    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다.")
    @Digits(integer = 10, fraction = 2, message = "가격 형식이 올바르지 않습니다.")
    private BigDecimal price;
    
    @Schema(description = "재고 수량", example = "150", required = false)
    @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
    @Max(value = 999999, message = "재고는 999,999를 초과할 수 없습니다.")
    private Integer stock;
    
    // 기본 생성자
    public UpdateProductRequest() {}
    
    // 생성자
    public UpdateProductRequest(String name, BigDecimal price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    
    /**
     * 수정할 필드가 있는지 확인
     */
    public boolean hasUpdates() {
        return (name != null && !name.trim().isEmpty()) || 
               price != null || 
               stock != null;
    }
    
    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("name", "상품명 (선택적)", "스마트폰 프로", false)
                .field("price", "상품 가격 (선택적)", "399000", false)
                .field("stock", "재고 수량 (선택적)", "150", false)
                .build();
    }
}