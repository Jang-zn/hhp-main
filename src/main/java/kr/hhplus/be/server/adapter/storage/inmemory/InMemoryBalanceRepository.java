package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile({"local", "test"}) // 특정 프로파일에서만 활성화
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
    public Optional<Balance> findByUser(@NotNull User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return Optional.ofNullable(userBalances.get(user.getId()));
    }

    @Override
    public Balance save(@NotNull Balance balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }
        if (balance.getUser() == null) {
            throw new IllegalArgumentException("Balance user cannot be null");
        }
        if (balance.getUser().getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        Long userId = balance.getUser().getId();
        
        // 동기화 블록을 사용하여 두 맵을 원자적으로 업데이트
        synchronized (this) {
            Balance existingBalance = userBalances.get(userId);
            Balance savedBalance;
            
            if (existingBalance != null) {
                savedBalance = Balance.builder()
                        .id(existingBalance.getId())
                        .user(balance.getUser())
                        .amount(balance.getAmount())
                        .createdAt(existingBalance.getCreatedAt())
                        .updatedAt(balance.getUpdatedAt())
                        .build();
            } else {
                Long id = balance.getId() != null ? balance.getId() : nextId.getAndIncrement();
                savedBalance = Balance.builder()
                        .id(id)
                        .user(balance.getUser())
                        .amount(balance.getAmount())
                        .createdAt(balance.getCreatedAt())
                        .updatedAt(balance.getUpdatedAt())
                        .build();
            }
            
            userBalances.put(userId, savedBalance);
            balances.put(savedBalance.getId(), savedBalance);
            
            return savedBalance;
        }
    }
} 