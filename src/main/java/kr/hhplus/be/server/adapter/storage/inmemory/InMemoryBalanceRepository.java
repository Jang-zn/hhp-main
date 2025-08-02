package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import kr.hhplus.be.server.domain.exception.BalanceException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("test_inmemory") // 특정 프로파일에서만 활성화
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory", matchIfMissing = true)
@Slf4j
public class InMemoryBalanceRepository implements BalanceRepositoryPort {

    private final Map<Long, Balance> balances = new ConcurrentHashMap<>();
    private final Map<Long, Balance> userBalances = new ConcurrentHashMap<>(); // userId -> Balance 매핑
    private final AtomicLong nextId = new AtomicLong(1L);

    @PostConstruct
    public void init() {
        log.warn("⚠️ InMemory 저장소를 사용합니다. 운영 환경에서는 사용하지 마세요!");
        log.info("InMemory Balance Repository 초기화 완료");
    }

    @PreDestroy
    public void cleanup() {
        log.info("InMemory Balance Repository 정리 중...");
        balances.clear();
        userBalances.clear();
    }

    @Override
    public Optional<Balance> findByUserId(@NotNull Long userId) {
        if (userId == null) {
            throw new BalanceException.UserIdAndAmountRequired();
        }
        return Optional.ofNullable(userBalances.get(userId));
    }

    @Override
    public Balance save(@NotNull Balance balance) {
        if (balance == null) {
            throw new BalanceException.BalanceCannotBeNull();
        }
        if (balance.getUserId() == null) {
            throw new BalanceException.UserIdAndAmountRequired();
        }
        if (balance.getAmount() == null) {
            throw new BalanceException.AmountCannotBeNull();
        }
        if (balance.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BalanceException.InvalidAmountPositive();
        }
        
        Long userId = balance.getUserId();
        
        // 동기화 블록을 사용하여 두 맵을 원자적으로 업데이트
        synchronized (this) {
            Balance existingBalance = userBalances.get(userId);

            if (existingBalance != null) {
                // 기존 엔티티 업데이트
                balance.onUpdate(); // updatedAt 설정
                // 기존 ID와 createdAt 유지
                balance.setId(existingBalance.getId());
                balance.setCreatedAt(existingBalance.getCreatedAt());
            } else {
                // 새로운 엔티티 생성
                balance.onCreate(); // createdAt, updatedAt 설정
                if (balance.getId() == null) {
                    balance.setId(nextId.getAndIncrement());
                }
            }

            userBalances.put(userId, balance);
            balances.put(balance.getId(), balance);

            return balance;
        }
    }
} 