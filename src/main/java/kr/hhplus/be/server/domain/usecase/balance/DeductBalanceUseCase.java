package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.BalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeductBalanceUseCase {
    
    private final BalanceRepositoryPort balanceRepositoryPort;
    
    public Balance execute(User user, BigDecimal amount) {
        log.debug("잔액 차감: userId={}, amount={}", user.getId(), amount);
        
        // 차감 금액 검증
        validateAmount(amount);
        
        // 잔액 조회
        Balance balance = balanceRepositoryPort.findByUser(user)
                .orElseThrow(() -> {
                    log.warn("잔액 정보 없음: userId={}", user.getId());
                    return new BalanceException.NotFound();
                });
        
        // 잔액 부족 확인
        if (balance.getAmount().compareTo(amount) < 0) {
            log.warn("잔액 부족: userId={}, balance={}, requiredAmount={}", 
                    user.getId(), balance.getAmount(), amount);
            throw new BalanceException.InsufficientBalance();
        }
        
        // 잔액 차감
        BigDecimal originalAmount = balance.getAmount();
        balance.subtractAmount(amount);
        
        // 저장
        Balance savedBalance = balanceRepositoryPort.save(balance);
        
        log.info("잔액 차감 완료: userId={}, 이전잔액={}, 차감금액={}, 현재잔액={}", 
                user.getId(), originalAmount, amount, savedBalance.getAmount());
        
        return savedBalance;
    }
    
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BalanceException.InvalidAmount();
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BalanceException.InvalidAmount();
        }
    }
}