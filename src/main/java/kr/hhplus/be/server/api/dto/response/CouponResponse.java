package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "쿠폰 응답")
public record CouponResponse(
        @Schema(description = "쿠폰 ID", example = "1")
        Long couponId,
        
        @Schema(description = "쿠폰 코드", example = "SUMMER2025")
        String code,
        
        @Schema(description = "할인율", example = "0.10")
        BigDecimal discountRate,
        
        @Schema(description = "유효기간", example = "2025-12-31T23:59:59")
        LocalDateTime validUntil
) implements DocumentedDto {
    
    @Override
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "couponId", new SchemaInfo("쿠폰 ID", "1"),
                "code", new SchemaInfo("쿠폰 코드", "SUMMER2025"),
                "discountRate", new SchemaInfo("할인율", "0.10"),
                "validUntil", new SchemaInfo("유효기간", "2025-12-31T23:59:59")
        );
    }
} 