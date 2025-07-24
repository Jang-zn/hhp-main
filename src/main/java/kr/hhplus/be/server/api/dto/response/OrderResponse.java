package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "주문 응답")
public record OrderResponse(
        @Schema(description = "주문 ID", example = "1")
        Long orderId,
        
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        
        @Schema(description = "주문 상태", example = "PENDING")
        String status,
        
        @Schema(description = "총 주문 금액", example = "25000")
        BigDecimal totalAmount,
        
        @Schema(description = "주문 생성 시간", example = "2024-01-01T12:00:00")
        LocalDateTime createdAt,
        
        @Schema(description = "주문 상품 목록")
        List<OrderItemResponse> items
) implements DocumentedDto {
    
    @Override
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "orderId", new SchemaInfo("주문 ID", "1"),
                "userId", new SchemaInfo("사용자 ID", "1"),
                "status", new SchemaInfo("주문 상태", "PENDING"),
                "totalAmount", new SchemaInfo("총 주문 금액", "25000"),
                "createdAt", new SchemaInfo("주문 생성 시간", "2024-01-01T12:00:00"),
                "items", new SchemaInfo("주문 상품 목록", "[]")
        );
    }
    
    @Schema(description = "주문 상품 정보")
    public record OrderItemResponse(
            @Schema(description = "상품 ID", example = "1")
            Long productId,
            
            @Schema(description = "상품명", example = "상품 A")
            String name,
            
            @Schema(description = "주문 수량", example = "2")
            int quantity,
            
            @Schema(description = "상품 가격", example = "10000")
            BigDecimal price
    ) {}
} 