package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
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
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 잔액 조회 (캐시 적용)
     * 
     * @param userId 사용자 ID
     * @return 사용자 잔액 정보
     */
    public Optional<Balance> execute(Long userId) {
        log.debug("잔액 조회 요청: userId={}", userId);
        
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("사용자 없음: userId={}", userId);
            throw new UserException.InvalidUser();
        }
        
        try {
            String cacheKey = keyGenerator.generateBalanceCacheKey(userId);
            
            // 캐시에서 조회 시도
            Balance cachedBalance = cachePort.get(cacheKey, Balance.class);
            
            if (cachedBalance != null) {
                log.debug("캐시에서 잔액 조회 성공: userId={}, amount={}", userId, cachedBalance.getAmount());
                return Optional.of(cachedBalance);
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            Optional<Balance> balanceOpt = balanceRepositoryPort.findByUserId(userId);
            if (balanceOpt.isPresent()) {
                Balance balance = balanceOpt.get();
                log.debug("데이터베이스에서 잔액 조회: userId={}, amount={}", userId, balance.getAmount());
                
                // TTL과 함께 캐시에 저장
                cachePort.put(cacheKey, balance, CacheTTL.USER_BALANCE.getSeconds());
                
                return Optional.of(balance);
            } else {
                log.debug("잔액 정보 없음: userId={}", userId);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("잔액 조회 중 오류 발생: userId={}", userId, e);
            // 캐시 오류 시 직접 DB에서 조회
            return balanceRepositoryPort.findByUserId(userId);
        }
    }
    
} 