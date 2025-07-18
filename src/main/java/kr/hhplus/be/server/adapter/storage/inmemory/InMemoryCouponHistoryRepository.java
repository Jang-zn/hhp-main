package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCouponHistoryRepository implements CouponHistoryRepositoryPort {

    private final Map<Long, CouponHistory> couponHistories = new ConcurrentHashMap<>();

    @Override
    public boolean existsByUserAndCoupon(kr.hhplus.be.server.domain.entity.User user, kr.hhplus.be.server.domain.entity.Coupon coupon) {
        return couponHistories.values().stream()
                .anyMatch(history -> history.getUser().equals(user) && history.getCoupon().equals(coupon));
    }

    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        couponHistories.put(couponHistory.getId(), couponHistory);
        return couponHistory;
    }

    @Override
    public List<CouponHistory> findByUserWithPagination(kr.hhplus.be.server.domain.entity.User user, int limit, int offset) {
        return couponHistories.values().stream()
                .filter(history -> history.getUser().equals(user))
                .skip(offset)
                .limit(limit)
                .toList();
    }
} 