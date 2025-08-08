package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.api.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
@Profile("test_inmemory")
public class InMemoryCouponHistoryRepository implements CouponHistoryRepositoryPort {

    private final Map<Long, CouponHistory> couponHistories = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    public void clear() {
        couponHistories.clear();
        nextId.set(1L);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        if (userId == null || couponId == null) {
            throw new CouponException.UserIdAndCouponIdRequired();
        }
        
        return couponHistories.values().stream()
                .anyMatch(history -> 
                    history.getUserId() != null && 
                    history.getCouponId() != null &&
                    history.getUserId().equals(userId) && 
                    history.getCouponId().equals(couponId));
    }

    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        if (couponHistory == null) {
            throw new CouponException.InvalidCouponHistoryData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (couponHistory.getUserId() == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (couponHistory.getCouponId() == null) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long historyId = couponHistory.getId() != null ? couponHistory.getId() : nextId.getAndIncrement();
        
        CouponHistory savedHistory = couponHistories.compute(historyId, (key, existingHistory) -> {
            if (existingHistory != null) {
                couponHistory.onUpdate();
                couponHistory.setId(existingHistory.getId());
                couponHistory.setCreatedAt(existingHistory.getCreatedAt());
                return couponHistory;
            } else {
                couponHistory.onCreate();
                if (couponHistory.getId() == null) {
                    couponHistory.setId(historyId);
                }
                return couponHistory;
            }
        });
        
        return savedHistory;
    }

    @Override
    public Optional<CouponHistory> findById(Long id) {
        if (id == null) {
            throw new CouponException.InvalidCouponHistoryData(ErrorCode.INVALID_INPUT.getMessage());
        }
        return Optional.ofNullable(couponHistories.get(id));
    }

    @Override
    public List<CouponHistory> findByUserIdWithPagination(Long userId, int limit, int offset) {
        if (userId == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (limit <= 0 || offset < 0) {
            throw new CouponException.InvalidPaginationParams(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUserId() != null && 
                    history.getUserId().equals(userId))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<CouponHistory> findByUserIdAndStatus(Long userId, CouponHistoryStatus status) {
        if (userId == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (status == null) {
            throw new CouponException.InvalidCouponHistoryData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUserId() != null && 
                    history.getUserId().equals(userId) &&
                    history.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status) {
        // InMemory 구현에서는 서비스 층에서 처리하도록 단순히 상태만 필터링
        return couponHistories.values().stream()
                .filter(history -> history.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public long countUsableCouponsByUserId(Long userId) {
        if (userId == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        // InMemory 구현에서는 서비스 층에서 만료 체크하도록 단순히 상태만 확인
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUserId() != null && 
                    history.getUserId().equals(userId) &&
                    history.getStatus() == CouponHistoryStatus.ISSUED)
                .count();
    }
} 