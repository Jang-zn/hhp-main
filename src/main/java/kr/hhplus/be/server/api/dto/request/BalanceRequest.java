package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "잔액 관련 요청")
public class BalanceRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    private Long userId;
    
    @Schema(description = "충전 금액", example = "10000", required = true)
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
    
    /**
     * 요청 데이터 검증
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (userId == null) {
            throw new IllegalArgumentException(ErrorCode.INVALID_USER_ID.getMessage());
        }
        if (userId <= 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_USER_ID.getMessage());
        }
        if (amount == null) {
            throw new IllegalArgumentException(ErrorCode.INVALID_AMOUNT.getMessage());
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(ErrorCode.NEGATIVE_AMOUNT.getMessage());
        }
    }

    @Override
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "userId", new SchemaInfo("사용자 ID", "1"),
                "amount", new SchemaInfo("충전 금액", "10000")
        );
    }
}