package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "쿠폰 응답")
public record CouponResponse(
        @Schema(description = "쿠폰 히스토리 ID", example = "1")
        Long couponHistoryId,
        
        @Schema(description = "쿠폰 ID", example = "1")
        Long couponId,
        
        @Schema(description = "쿠폰 코드", example = "SUMMER2025")
        String code,
        
        @Schema(description = "할인율", example = "0.10")
        BigDecimal discountRate,
        
        @Schema(description = "유효기간", example = "2025-12-31T23:59:59")
        LocalDateTime validUntil,
        
        @Schema(description = "쿠폰 상태", example = "ACTIVE")
        CouponStatus couponStatus,
        
        @Schema(description = "쿠폰 히스토리 상태", example = "ISSUED")
        CouponHistoryStatus historyStatus,
        
        @Schema(description = "발급일시", example = "2025-01-01T00:00:00")
        LocalDateTime issuedAt,
        
        @Schema(description = "사용일시", example = "2025-01-15T10:30:00")
        LocalDateTime usedAt,
        
        @Schema(description = "사용 가능 여부", example = "true")
        boolean usable
) implements DocumentedDto {
    
    @Override
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "couponHistoryId", new SchemaInfo("쿠폰 히스토리 ID", "1"),
                "couponId", new SchemaInfo("쿠폰 ID", "1"),
                "code", new SchemaInfo("쿠폰 코드", "SUMMER2025"),
                "discountRate", new SchemaInfo("할인율", "0.10"),
                "validUntil", new SchemaInfo("유효기간", "2025-12-31T23:59:59"),
                "couponStatus", new SchemaInfo("쿠폰 상태", "ACTIVE"),
                "historyStatus", new SchemaInfo("쿠폰 히스토리 상태", "ISSUED"),
                "issuedAt", new SchemaInfo("발급일시", "2025-01-01T00:00:00"),
                "usedAt", new SchemaInfo("사용일시", "2025-01-15T10:30:00"),
                "usable", new SchemaInfo("사용 가능 여부", "true")
        );
    }
} 