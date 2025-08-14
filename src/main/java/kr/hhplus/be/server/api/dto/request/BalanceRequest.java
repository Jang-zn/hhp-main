package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import kr.hhplus.be.server.api.ErrorCode;

import java.math.BigDecimal;

@Schema(description = "잔액 관련 요청")
public class BalanceRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    @NotNull
    @Positive
    private Long userId;
    
    @Schema(description = "충전 금액", example = "10000", required = true)
    @NotNull
    @DecimalMin(value = "1000")
    @DecimalMax(value = "1000000")
    @Positive
    private BigDecimal amount;

    // ChargeBalanceUseCase와 동일한 상수 정의
    private static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("1000000");

    // 기본 생성자
    public BalanceRequest() {}

    // 생성자
    public BalanceRequest(Long userId, BigDecimal amount) {
        this.userId = userId;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    

    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("userId", "사용자 ID", "1")
                .field("amount", "충전 금액", "10000")
                .build();
    }
    
    /**
     * 잔액 충전 요청
     */
    @Schema(description = "잔액 충전 요청")
    public static class ChargeBalanceRequest {
        @Schema(description = "사용자 ID", example = "1", required = true)
        @NotNull
        @Positive
        private Long userId;
        
        @Schema(description = "충전 금액", example = "10000", required = true)
        @NotNull
        @DecimalMin(value = "1000")
        @DecimalMax(value = "1000000")
        @Positive
        private BigDecimal amount;
        
        public ChargeBalanceRequest() {}
        
        public ChargeBalanceRequest(Long userId, BigDecimal amount) {
            this.userId = userId;
            this.amount = amount;
        }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}