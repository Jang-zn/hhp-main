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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 잔액 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 분산 락을 사용하여 동시성 제어를 보장하며,
 * 잔액 조회, 충전 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

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
        
        return getBalanceUseCase.execute(userId);
    }

    /**
     * 사용자 잔액 충전
     * 
     * 동시성 제어를 위해 분산 락을 사용합니다.
     * 
     * @param userId 사용자 ID
     * @param chargeAmount 충전 금액
     * @return 충전 후 잔액 정보
     */
    @Transactional
    public Balance chargeBalance(Long userId, BigDecimal chargeAmount) {
        String lockKey = "balance-" + userId;
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            throw new UserException.NotFound();
        }
        
        // 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return chargeBalanceUseCase.execute(userId, chargeAmount);
        } finally {
            // 락 해제
            lockingPort.releaseLock(lockKey);
        }
    }
}