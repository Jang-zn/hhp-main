package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargeBalanceUseCase {
    
    private final BalanceRepositoryPort balanceRepositoryPort;
    
    private static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("1000000");
    
    /**
     * 잔액을 충전합니다.
     * 
     * 동시성 제어:
     * - 낙관적 락 (@Version) 사용으로 동시 충전 방지
     * - OptimisticLockingFailureException 발생 시 최대 3회 재시도
     * - 트랜잭션 타임아웃 3초 설정
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 잔액 엔티티
     */
    @Transactional(timeout = 3)
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3)
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
    
    /**
     * 낙관적 락 충돌로 재시도가 모두 실패했을 때 호출되는 복구 메서드
     * 
     * @param ex 최종 발생한 OptimisticLockingFailureException
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 예외 발생
     * @throws BalanceException.ConcurrencyConflict 동시성 충돌 예외
     */
    @Recover
    public Balance recover(OptimisticLockingFailureException ex, Long userId, BigDecimal amount) {
        log.error("잔액 충전 재시도 모두 실패: userId={}, amount={}, error={}", 
                userId, amount, ex.getMessage());
        throw new BalanceException.ConcurrencyConflict();
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