package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "잔액 충전 요청")
public class BalanceChargeRequest {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
    
    @Schema(description = "충전 금액", example = "10000", required = true)
    @NotNull(message = "충전 금액은 필수입니다")
    @DecimalMin(value = "0.0", inclusive = false, message = "충전 금액은 0보다 커야 합니다")
    private BigDecimal amount;

    // 기본 생성자
    public BalanceChargeRequest() {}

    // 생성자
    public BalanceChargeRequest(Long userId, BigDecimal amount) {
        this.userId = userId;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
} 