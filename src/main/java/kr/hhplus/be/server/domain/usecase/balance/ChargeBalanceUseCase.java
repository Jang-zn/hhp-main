package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargeBalanceUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    
    private static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("1000000");
    private static final int CACHE_TTL_SECONDS = 600;
    
    @Transactional
    public Balance execute(Long userId, BigDecimal amount) {
        log.info("잔액 충전 요청: userId={}, amount={}", userId, amount);
        
        // 입력 값 검증 (락 획득 전에 빠르게 검증)
        validateInputs(userId, amount);
        
        // 입력이 유효한 경우에만 락 획득
        String lockKey = "balance-" + userId;
        if (!lockingPort.acquireLock(lockKey)) {
            log.warn("락 획득 실패: userId={}", userId);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 사용자 조회
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("사용자 없음: userId={}", userId);
                        return new UserException.InvalidUser();
                    });
            
            // 기존 잔액 조회 또는 새 잔액 생성
            Balance balance = balanceRepositoryPort.findByUser(user)
                    .orElse(Balance.builder().user(user).amount(BigDecimal.ZERO).build());
            
            BigDecimal originalAmount = balance.getAmount();
            
            // 잔액 충전
            balance.addAmount(amount);
            
            // 저장
            Balance savedBalance = balanceRepositoryPort.save(balance);
            
            // 캐시 업데이트
            updateCache(userId, savedBalance);
            
            log.info("잔액 충전 완료: userId={}, 이전잔액={}, 충전금액={}, 현재잔액={}", 
                    userId, originalAmount, amount, savedBalance.getAmount());
            
            return savedBalance;
            
        } catch (BalanceException e) {
            log.error("잔액 충전 실패: userId={}, amount={}, error={}", userId, amount, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("잔액 충전 중 예상치 못한 오류: userId={}, amount={}", userId, amount, e);
            throw new CommonException.ConcurrencyConflict();
        } finally {
            lockingPort.releaseLock(lockKey);
            log.debug("락 해제 완료: userId={}", userId);
        }
    }
    
    private void validateInputs(Long userId, BigDecimal amount) {
        if (userId == null) {
            throw new UserException.InvalidUser();
        }
        
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
    
    private void updateCache(Long userId, Balance balance) {
        try {
            cachePort.put("balance:" + userId, balance, CACHE_TTL_SECONDS);
            log.debug("캐시 업데이트 완료: userId={}", userId);
        } catch (Exception e) {
            log.warn("캐시 업데이트 실패: userId={}, error={}", userId, e.getMessage());
            // 캐시 실패는 전체 프로세스를 중단시키지 않음
        }
    }
} 