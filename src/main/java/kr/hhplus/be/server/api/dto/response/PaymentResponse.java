package kr.hhplus.be.server.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("paymentId", "결제 ID", "1")
                .field("orderId", "주문 ID", "1")
                .field("status", "결제 상태", "COMPLETED")
                .field("finalAmount", "최종 결제 금액", "23000")
                .field("paidAt", "결제 완료 시간", "2024-01-01T12:05:00")
                .build();
    }
} 