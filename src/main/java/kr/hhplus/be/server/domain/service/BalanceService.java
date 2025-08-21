package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * 잔액 관련 비즈니스 로직을 처리하는 서비스
 * 
 * UseCase 레이어에 위임하여 잔액 조회, 충전 등의 기능을 제공합니다.
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
    private final KeyGenerator keyGenerator;
    
    /**
     * 사용자 잔액 조회
     * 
     * @param userId 사용자 ID
     * @return 사용자 잔액 정보
     */
    public Balance getBalance(Long userId) {
        log.debug("잔액 조회 요청: userId={}", userId);
        
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        return getBalanceUseCase.execute(userId)
                .orElseThrow(() -> new RuntimeException("Balance not found"));
    }

    /**
     * 사용자 잔액 충전
     * 
     * @param userId 사용자 ID
     * @param chargeAmount 충전 금액
     * @return 충전 후 잔액 정보
     */
    public Balance chargeBalance(Long userId, BigDecimal chargeAmount) {
        log.info("잔액 충전 요청: userId={}, amount={}", userId, chargeAmount);
        
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
            
            log.info("잔액 충전 완료: userId={}, newAmount={}", userId, result.getAmount());
            return result;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
}