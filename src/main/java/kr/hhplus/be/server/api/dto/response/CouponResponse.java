package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "쿠폰 응답")
public record CouponResponse(
        @Schema(description = "쿠폰 히스토리 ID", example = "1")
        Long couponHistoryId,
        
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        
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
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("couponHistoryId", "쿠폰 히스토리 ID", "1")
                .field("userId", "사용자 ID", "1")
                .field("couponId", "쿠폰 ID", "1")
                .field("code", "쿠폰 코드", "SUMMER2025")
                .field("discountRate", "할인율", "0.10")
                .field("validUntil", "유효기간", "2025-12-31T23:59:59")
                .field("couponStatus", "쿠폰 상태", "ACTIVE")
                .field("historyStatus", "쿠폰 히스토리 상태", "ISSUED")
                .field("issuedAt", "발급일시", "2025-01-01T00:00:00")
                .field("usedAt", "사용일시", "2025-01-15T10:30:00")
                .field("usable", "사용 가능 여부", "true")
                .build();
    }
} 