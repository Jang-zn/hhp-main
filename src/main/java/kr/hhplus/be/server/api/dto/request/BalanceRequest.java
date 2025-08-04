package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import kr.hhplus.be.server.domain.exception.BalanceException;
import kr.hhplus.be.server.domain.exception.UserException;

import java.math.BigDecimal;

@Schema(description = "잔액 관련 요청")
public class BalanceRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    private Long userId;
    
    @Schema(description = "충전 금액", example = "10000", required = true)
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
    
    /**
     * 요청 데이터 검증
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (userId == null || userId <= 0) { // userId가 null이거나 0 이하일 경우
            throw new UserException.InvalidUser();
        }
        if (amount == null) {
            throw new BalanceException.InvalidAmount();
        }
        // 금액 범위 검증 추가
        if (amount.compareTo(MIN_CHARGE_AMOUNT) < 0 || amount.compareTo(MAX_CHARGE_AMOUNT) > 0) {
            throw new BalanceException.InvalidAmount();
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) { // 0 이하일 경우
            throw new BalanceException.InvalidAmount();
        }
    }

    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("userId", "사용자 ID", "1")
                .field("amount", "충전 금액", "10000")
                .build();
    }
}