package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kr.hhplus.be.server.domain.exception.*;

import java.math.BigDecimal;

@Schema(description = "잔액 관련 요청")
public class BalanceRequest {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    @NotNull(message = UserException.Messages.INVALID_USER_ID)
    @Positive(message = UserException.Messages.INVALID_USER_ID_POSITIVE)
    private Long userId;
    
    @Schema(description = "충전 금액", example = "10000", required = true)
    @NotNull(message = BalanceException.Messages.INVALID_AMOUNT_REQUIRED)
    @DecimalMin(value = "0.0", inclusive = false, message = BalanceException.Messages.INVALID_AMOUNT_POSITIVE)
    private BigDecimal amount;

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
}