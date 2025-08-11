package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * 잔액 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 분산 락을 사용하여 동시성 제어를 보장하며,
 * 잔액 조회, 충전 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final TransactionTemplate transactionTemplate;
    private final ChargeBalanceUseCase chargeBalanceUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;

    /**
     * 사용자 잔액 조회
     * 
     * @param userId 사용자 ID
     * @return 사용자 잔액 정보
     */
    public Balance getBalance(Long userId) {
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        return getBalanceUseCase.execute(userId)
                .orElseThrow(() -> new RuntimeException("Balance not found"));
    }

    /**
     * 사용자 잔액 충전
     * 
     * 동시성 제어를 위해 분산 락을 사용하고, TransactionTemplate으로 명시적 트랜잭션 관리합니다.
     * 실행 순서: Lock 획득 → Transaction 시작 → Logic 실행 → Transaction 종료 → Lock 해제
     * 
     * @param userId 사용자 ID
     * @param chargeAmount 충전 금액
     * @return 충전 후 잔액 정보
     */
    public Balance chargeBalance(Long userId, BigDecimal chargeAmount) {
        String lockKey = "balance-" + userId;
        
        // 사용자 존재 확인 (트랜잭션 외부에서)
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        // 1. 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 2. 명시적 트랜잭션 실행
            return transactionTemplate.execute(status -> {
                // 3. 비즈니스 로직 실행 (트랜잭션 내)
                return chargeBalanceUseCase.execute(userId, chargeAmount);
            });
        } finally {
            // 4. 락 해제
            lockingPort.releaseLock(lockKey);
        }
    }
}