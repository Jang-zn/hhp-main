package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Coupon;
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
    public boolean existsByUserAndCoupon(User user, Coupon coupon) {
        if (user == null || coupon == null) {
            throw new CouponException.UserIdAndCouponIdRequired();
        }
        if (user.getId() == null || coupon.getId() == null) {
            throw new CouponException.UserIdAndCouponIdRequired();
        }
        
        return couponHistories.values().stream()
                .anyMatch(history -> 
                    history.getUser() != null && 
                    history.getCoupon() != null &&
                    history.getUser().getId().equals(user.getId()) && 
                    history.getCoupon().getId().equals(coupon.getId()));
    }

    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        if (couponHistory == null) {
            throw new CouponException.InvalidCouponHistoryData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (couponHistory.getUser() == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (couponHistory.getCoupon() == null) {
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
    public List<CouponHistory> findByUserWithPagination(User user, int limit, int offset) {
        if (user == null || user.getId() == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (limit <= 0 || offset < 0) {
            throw new CouponException.InvalidPaginationParams(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUser() != null && 
                    history.getUser().getId().equals(user.getId()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<CouponHistory> findByUserAndStatus(User user, CouponHistoryStatus status) {
        if (user == null || user.getId() == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (status == null) {
            throw new CouponException.InvalidCouponHistoryData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUser() != null && 
                    history.getUser().getId().equals(user.getId()) &&
                    history.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponHistory> findExpiredHistoriesInStatus(LocalDateTime now, CouponHistoryStatus status) {
        return couponHistories.values().stream()
                .filter(history -> history.getStatus() == status)
                .filter(history -> history.getCoupon() != null && 
                        now.isAfter(history.getCoupon().getEndDate()))
                .collect(Collectors.toList());
    }

    @Override
    public long countUsableCouponsByUser(User user) {
        if (user == null || user.getId() == null) {
            throw new CouponException.InvalidUserData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        LocalDateTime now = LocalDateTime.now();
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUser() != null && 
                    history.getUser().getId().equals(user.getId()) &&
                    history.getStatus() == CouponHistoryStatus.ISSUED &&
                    history.getCoupon() != null &&
                    now.isBefore(history.getCoupon().getEndDate()))
                .count();
    }
} 