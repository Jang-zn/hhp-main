package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import kr.hhplus.be.server.domain.exception.BalanceException;

import java.math.BigDecimal;

@Entity
@Table(
    name = "balances",
    indexes = {
        @Index(name = "idx_balance_user", columnList = "user_id"),
        @Index(name = "idx_balance_amount", columnList = "amount")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = "user") // 순환 참조 방지
public class Balance extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NotNull
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00", message = "잔액은 0 이상이어야 합니다")
    private BigDecimal amount;

    // @Version // 낙관적 락 - JPA 사용 시 사용
    // private Long version;

    // 비즈니스 메서드
    public void addAmount(@NotNull BigDecimal amount) {
        validateAmount(amount);
        this.amount = this.amount.add(amount);
    }

    public void subtractAmount(@NotNull BigDecimal amount) {
        validateAmount(amount);
        if (this.amount.compareTo(amount) < 0) {
            throw new BalanceException.InsufficientBalance();
        }
        this.amount = this.amount.subtract(amount);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BalanceException.InvalidAmount();
        }
    }
} 