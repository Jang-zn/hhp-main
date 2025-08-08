package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "잔액 조회 응답")
public record BalanceResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        @Schema(description = "현재 잔액", example = "50000")
        BigDecimal amount,
        @Schema(description = "마지막 업데이트 시간", example = "2024-01-01T12:00:00")
        LocalDateTime updatedAt
) implements DocumentedDto {
    
    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("userId", "사용자 ID", "1")
                .field("amount", "현재 잔액", "50000")
                .field("updatedAt", "마지막 업데이트 시간", "2024-01-01T12:00:00")
                .build();
    }
} 