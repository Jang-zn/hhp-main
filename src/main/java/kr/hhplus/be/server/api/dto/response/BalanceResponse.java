package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "userId", new SchemaInfo("사용자 ID", "1"),
                "amount", new SchemaInfo("현재 잔액", "50000"),
                "updatedAt", new SchemaInfo("마지막 업데이트 시간", "2024-01-01T12:00:00")
        );
    }
} 