package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.BalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeductBalanceUseCase {
    
    private final BalanceRepositoryPort balanceRepositoryPort;
    
    /**
     * 잔액을 차감합니다.
     * 
     * 동시성 제어:
     * - 낙관적 락 (@Version) 사용으로 동시 차감 방지
     * - OptimisticLockingFailureException 발생 시 최대 3회 재시도
     * 
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @return 차감 후 잔액 엔티티
     */
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3)
    public Balance execute(Long userId, BigDecimal amount) {
        log.debug("잔액 차감: userId={}, amount={}", userId, amount);
        
        // 차감 금액 검증
        validateAmount(amount);
        
        // 잔액 조회
        Balance balance = balanceRepositoryPort.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("잔액 정보 없음: userId={}", userId);
                    return new BalanceException.NotFound();
                });
        
        // 잔액 부족 확인
        if (balance.getAmount().compareTo(amount) < 0) {
            log.warn("잔액 부족: userId={}, balance={}, requiredAmount={}", 
                    userId, balance.getAmount(), amount);
            throw new BalanceException.InsufficientBalance();
        }
        
        // 잔액 차감
        BigDecimal originalAmount = balance.getAmount();
        balance.subtractAmount(amount);
        
        // 저장
        Balance savedBalance = balanceRepositoryPort.save(balance);
        
        log.info("잔액 차감 완료: userId={}, 이전잔액={}, 차감금액={}, 현재잔액={}", 
                userId, originalAmount, amount, savedBalance.getAmount());
        
        return savedBalance;
    }
    
    /**
     * 낙관적 락 충돌로 재시도가 모두 실패했을 때 호출되는 복구 메서드
     * 
     * @param ex 최종 발생한 OptimisticLockingFailureException
     * @param userId 사용자 ID
     * @param amount 차감 금액
     * @return 예외 발생
     * @throws BalanceException.ConcurrencyConflict 동시성 충돌 예외
     */
    @Recover
    public Balance recover(OptimisticLockingFailureException ex, Long userId, BigDecimal amount) {
        log.error("잔액 차감 재시도 모두 실패: userId={}, amount={}, error={}", 
                userId, amount, ex.getMessage());
        throw new BalanceException.ConcurrencyConflict();
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