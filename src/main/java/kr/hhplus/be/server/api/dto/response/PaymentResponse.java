package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "결제 응답")
public record PaymentResponse(
        @Schema(description = "결제 ID", example = "1")
        Long paymentId,
        
        @Schema(description = "주문 ID", example = "1")
        Long orderId,
        
        @Schema(description = "결제 상태", example = "COMPLETED")
        String status,
        
        @Schema(description = "최종 결제 금액", example = "23000")
        BigDecimal finalAmount,
        
        @Schema(description = "결제 완료 시간", example = "2024-01-01T12:05:00")
        LocalDateTime paidAt
) implements DocumentedDto {
    
    @Override
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "paymentId", new SchemaInfo("결제 ID", "1"),
                "orderId", new SchemaInfo("주문 ID", "1"),
                "status", new SchemaInfo("결제 상태", "COMPLETED"),
                "finalAmount", new SchemaInfo("최종 결제 금액", "23000"),
                "paidAt", new SchemaInfo("결제 완료 시간", "2024-01-01T12:05:00")
        );
    }
} 