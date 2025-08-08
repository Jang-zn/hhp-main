package kr.hhplus.be.server.domain.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
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
    
    private static final int CACHE_TTL_SECONDS = 600;
    
    public Optional<Balance> execute(Long userId) {
        log.debug("잔액 조회 요청: userId={}", userId);
        
        // 입력 값 검증
        if (userId == null) {
            log.warn("잘못된 사용자 ID: null");
            throw new UserException.InvalidUser();
        }
        
        try {
            // 캐시에서 조회 시도
            String cacheKey = "balance:" + userId;
            Balance cachedBalance = getCachedBalance(cacheKey);
            
            if (cachedBalance != null) {
                log.debug("캐시에서 잔액 조회 성공: userId={}, amount={}", userId, cachedBalance.getAmount());
                return Optional.of(cachedBalance);
            }
            
            log.debug("캐시 미스, DB에서 조회: userId={}", userId);
            
            // 사용자 존재 확인
            if (!userRepositoryPort.existsById(userId)) {
                log.warn("사용자 없음: userId={}", userId);
                throw new UserException.InvalidUser();
            }
            
            // 잔액 조회
            Optional<Balance> balanceOpt = balanceRepositoryPort.findByUserId(userId);
            
            if (balanceOpt.isPresent()) {
                Balance balance = balanceOpt.get();
                // 캐시 업데이트
                updateCache(cacheKey, balance);
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
    
    private Balance getCachedBalance(String cacheKey) {
        try {
            return cachePort.get(cacheKey, Balance.class, () -> null);
        } catch (Exception e) {
            log.warn("캐시 조회 실패: cacheKey={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }
    
    private void updateCache(String cacheKey, Balance balance) {
        try {
            cachePort.put(cacheKey, balance, CACHE_TTL_SECONDS);
            log.debug("캐시 업데이트 완료: cacheKey={}", cacheKey);
        } catch (Exception e) {
            log.warn("캐시 업데이트 실패: cacheKey={}, error={}", cacheKey, e.getMessage());
            // 캐시 실패는 전체 프로세스를 중단시키지 않음
        }
    }
} 