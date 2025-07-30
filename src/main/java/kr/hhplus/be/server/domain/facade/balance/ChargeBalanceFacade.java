package kr.hhplus.be.server.domain.facade.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class ChargeBalanceFacade {

    private final ChargeBalanceUseCase chargeBalanceUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;

    public ChargeBalanceFacade(ChargeBalanceUseCase chargeBalanceUseCase, LockingPort lockingPort, UserRepositoryPort userRepositoryPort) {
        this.chargeBalanceUseCase = chargeBalanceUseCase;
        this.lockingPort = lockingPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    @Transactional
    public Balance chargeBalance(Long userId, BigDecimal chargeAmount) {
        String lockKey = "balance-" + userId;
        
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserException.NotFound());
        
        // 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return chargeBalanceUseCase.execute(user, chargeAmount);
        } finally {
            // 락 해제
            lockingPort.releaseLock(lockKey);
        }
    }
}