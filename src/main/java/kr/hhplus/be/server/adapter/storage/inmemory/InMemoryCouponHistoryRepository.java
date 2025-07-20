package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCouponHistoryRepository implements CouponHistoryRepositoryPort {

    private final Map<Long, CouponHistory> couponHistories = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public boolean existsByUserAndCoupon(kr.hhplus.be.server.domain.entity.User user, kr.hhplus.be.server.domain.entity.Coupon coupon) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (coupon == null) {
            throw new IllegalArgumentException("Coupon cannot be null");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (coupon.getId() == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
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
            throw new IllegalArgumentException("CouponHistory cannot be null");
        }
        if (couponHistory.getUser() == null) {
            throw new IllegalArgumentException("CouponHistory user cannot be null");
        }
        if (couponHistory.getCoupon() == null) {
            throw new IllegalArgumentException("CouponHistory coupon cannot be null");
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long historyId = couponHistory.getId() != null ? couponHistory.getId() : nextId.getAndIncrement();
        
        CouponHistory savedHistory = couponHistories.compute(historyId, (key, existingHistory) -> {
            if (existingHistory != null) {
                // 기존 히스토리 업데이트
                return CouponHistory.builder()
                        .id(existingHistory.getId())
                        .user(couponHistory.getUser())
                        .coupon(couponHistory.getCoupon())
                        .issuedAt(couponHistory.getIssuedAt())
                        .build();
            } else {
                // 새로운 히스토리 생성
                return CouponHistory.builder()
                        .id(historyId)
                        .user(couponHistory.getUser())
                        .coupon(couponHistory.getCoupon())
                        .issuedAt(couponHistory.getIssuedAt())
                        .build();
            }
        });
        
        return savedHistory;
    }

    @Override
    public List<CouponHistory> findByUserWithPagination(kr.hhplus.be.server.domain.entity.User user, int limit, int offset) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        
        return couponHistories.values().stream()
                .filter(history -> 
                    history.getUser() != null && 
                    history.getUser().getId().equals(user.getId()))
                .skip(offset)
                .limit(limit)
                .toList();
    }
} 