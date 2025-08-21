package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;

import java.math.BigDecimal;

@Schema(description = "상품 생성 요청")
public class CreateProductRequest implements DocumentedDto {
    
    @Schema(description = "상품명", example = "스마트폰", required = true)
    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 100자를 초과할 수 없습니다.")
    private String name;
    
    @Schema(description = "상품 가격", example = "299000", required = true)
    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다.")
    @Digits(integer = 10, fraction = 2, message = "가격 형식이 올바르지 않습니다.")
    private BigDecimal price;
    
    @Schema(description = "재고 수량", example = "100", required = true)
    @NotNull(message = "재고 수량은 필수입니다.")
    @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
    @Max(value = 999999, message = "재고는 999,999를 초과할 수 없습니다.")
    private Integer stock;
    
    // 기본 생성자
    public CreateProductRequest() {}
    
    // 생성자
    public CreateProductRequest(String name, BigDecimal price, Integer stock) {
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
    
    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("name", "상품명", "스마트폰", true)
                .field("price", "상품 가격", "299000", true)
                .field("stock", "재고 수량", "100", true)
                .build();
    }
}