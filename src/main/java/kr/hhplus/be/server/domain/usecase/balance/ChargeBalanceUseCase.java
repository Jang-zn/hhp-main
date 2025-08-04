package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargeBalanceUseCase {
    
    private final BalanceRepositoryPort balanceRepositoryPort;
    
    private static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("1000000");
    
    public Balance execute(Long userId, BigDecimal amount) {
        log.info("잔액 충전 요청: userId={}, amount={}", userId, amount);
        
        // 입력 값 검증
        validateUserId(userId);
        validateAmount(amount);
        
        // 기존 잔액 조회 또는 새 잔액 생성
        Balance balance = balanceRepositoryPort.findByUserId(userId)
                .orElse(Balance.builder().userId(userId).amount(BigDecimal.ZERO).build());
        
        BigDecimal originalAmount = balance.getAmount();
        
        // 잔액 충전
        balance.addAmount(amount);
        
        // 저장
        Balance savedBalance = balanceRepositoryPort.save(balance);
        
        log.info("잔액 충전 완료: userId={}, 이전잔액={}, 충전금액={}, 현재잔액={}", 
                userId, originalAmount, amount, savedBalance.getAmount());
        
        return savedBalance;
    }
    
    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new UserException.UserIdCannotBeNull();
        }
        
        if (userId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }
    }
    
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BalanceException.InvalidAmount();
        }
        
        if (amount.compareTo(MIN_CHARGE_AMOUNT) < 0 || amount.compareTo(MAX_CHARGE_AMOUNT) > 0) {
            throw new BalanceException.InvalidAmount();
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BalanceException.InvalidAmount();
        }
    }
} 