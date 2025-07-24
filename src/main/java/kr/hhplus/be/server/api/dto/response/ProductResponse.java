package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import java.math.BigDecimal;
import java.util.Map;

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
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "productId", new SchemaInfo("상품 ID", "1"),
                "name", new SchemaInfo("상품명", "상품 A"),
                "price", new SchemaInfo("상품 가격", "10000"),
                "stock", new SchemaInfo("재고 수량", "100")
        );
    }
} 