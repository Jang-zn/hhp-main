package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 잔액 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 분산 락을 사용하여 동시성 제어를 보장하며,
 * 잔액 조회, 충전 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final TransactionTemplate transactionTemplate;
    private final ChargeBalanceUseCase chargeBalanceUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 사용자 잔액 조회 (캐시 적용)
     * 
     * @param userId 사용자 ID
     * @return 사용자 잔액 정보
     */
    public Balance getBalance(Long userId) {
        log.debug("잔액 조회 요청: userId={}", userId);
        
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        try {
            String cacheKey = keyGenerator.generateBalanceCacheKey(userId);
            
            // 캐시에서 조회 시도
            Balance cachedBalance = cachePort.get(cacheKey, Balance.class);
            
            if (cachedBalance != null) {
                log.debug("캐시에서 잔액 조회 성공: userId={}, amount={}", userId, cachedBalance.getAmount());
                return cachedBalance;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            Optional<Balance> balanceOpt = getBalanceUseCase.execute(userId);
            if (balanceOpt.isPresent()) {
                Balance balance = balanceOpt.get();
                log.debug("데이터베이스에서 잔액 조회: userId={}, amount={}", userId, balance.getAmount());
                
                // TTL과 함께 캐시에 저장
                cachePort.put(cacheKey, balance, CacheTTL.USER_BALANCE.getSeconds());
                
                return balance;
            } else {
                log.debug("잔액 정보 없음: userId={}", userId);
                throw new RuntimeException("Balance not found");
            }
        } catch (Exception e) {
            log.error("잔액 조회 중 오류 발생: userId={}", userId, e);
            // 캐시 오류 시 직접 DB에서 조회
            return getBalanceUseCase.execute(userId)
                    .orElseThrow(() -> new RuntimeException("Balance not found"));
        }
    }

    /**
     * 사용자 잔액 충전
     * 
     * 동시성 제어를 위해 분산 락을 사용하고, TransactionTemplate으로 명시적 트랜잭션 관리.
     * 실행 순서: Lock 획득 → Transaction 시작 → Logic 실행 → Transaction 종료 → Lock 해제
     * 
     * @param userId 사용자 ID
     * @param chargeAmount 충전 금액
     * @return 충전 후 잔액 정보
     */
    public Balance chargeBalance(Long userId, BigDecimal chargeAmount) {
        String lockKey = keyGenerator.generateBalanceKey(userId);
        
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            Balance result = transactionTemplate.execute(status -> {
                return chargeBalanceUseCase.execute(userId, chargeAmount);
            });
            
            // 트랜잭션 커밋 후 Write-Through: 새로운 잔액을 캐시에 즉시 업데이트
            String cacheKey = keyGenerator.generateBalanceCacheKey(userId);
            cachePort.put(cacheKey, result, CacheTTL.USER_BALANCE.getSeconds());
            
            return result;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
}