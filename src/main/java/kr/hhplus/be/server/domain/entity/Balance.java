package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.exception.BalanceException;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Entity
@Table(
    name = "balances",
    indexes = {
        @Index(name = "idx_balance_user", columnList = "user_id"),
        @Index(name = "idx_balance_amount", columnList = "amount")
    }
)
@Check(constraints = "amount >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
@ToString
public class Balance extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    @NotNull
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    // 비즈니스 메서드
    public void addAmount(@NotNull @Positive BigDecimal amount) {
        this.amount = this.amount.add(amount);
    }

    /**
     * 잔액을 차감합니다.
     * DB @Check 제약조건으로 추가 무결성 보장:
     * - amount >= 0: 잔액이 음수가 될 수 없음
     * 
     * @param amount 차감할 금액
     * @throws BalanceException.InsufficientBalance 잔액 부족 시
     * @throws IllegalArgumentException 유효하지 않은 금액 시
     */
    public void subtractAmount(@NotNull @Positive BigDecimal amount) {
        if (this.amount.compareTo(amount) < 0) {
            throw new BalanceException.InsufficientBalance();
        }
        this.amount = this.amount.subtract(amount);
        // DB @Check 제약 조건이 추가 검증 수행하여 데이터 무결성 보장
    }

} 