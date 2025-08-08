package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import java.math.BigDecimal;

@Schema(description = "상품 응답")
public record ProductResponse(
        @Schema(description = "상품 ID", example = "1")
        Long productId,
        
        @Schema(description = "상품명", example = "상품 A")
        String name,
        
        @Schema(description = "상품 가격", example = "10000")
        BigDecimal price,
        
        @Schema(description = "재고 수량", example = "100")
        int stock
) implements DocumentedDto {
    
    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("productId", "상품 ID", "1")
                .field("name", "상품명", "상품 A")
                .field("price", "상품 가격", "10000")
                .field("stock", "재고 수량", "100")
                .build();
    }
} 