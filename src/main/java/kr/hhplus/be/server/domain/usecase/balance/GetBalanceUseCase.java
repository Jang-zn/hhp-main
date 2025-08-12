package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetBalanceUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    
    
    public Optional<Balance> execute(Long userId) {
        log.debug("잔액 조회 요청: userId={}", userId);
        
        // 입력 값 검증
        if (userId == null) {
            log.warn("잘못된 사용자 ID: null");
            throw new UserException.InvalidUser();
        }
        
        try {
            
            // 사용자 존재 확인
            if (!userRepositoryPort.existsById(userId)) {
                log.warn("사용자 없음: userId={}", userId);
                throw new UserException.InvalidUser();
            }
            
            // 잔액 조회
            Optional<Balance> balanceOpt = balanceRepositoryPort.findByUserId(userId);
            
            if (balanceOpt.isPresent()) {
                Balance balance = balanceOpt.get();
                log.debug("잔액 조회 성공: userId={}, amount={}", userId, balance.getAmount());
                return Optional.of(balance);
            } else {
                log.debug("잔액 없음: userId={}", userId);
                return Optional.empty();
            }
            
        } catch (BalanceException e) {
            log.error("잔액 조회 실패: userId={}, error={}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("잔액 조회 중 예상치 못한 오류: userId={}", userId, e);
            throw new UserException.InvalidUser();
        }
    }
    
} 