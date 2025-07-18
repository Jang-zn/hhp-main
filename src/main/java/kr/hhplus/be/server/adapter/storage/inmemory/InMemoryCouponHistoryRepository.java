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
    
    private final Map<String, CouponHistory> couponHistories = new ConcurrentHashMap<>();
    
    @Override
    public Optional<CouponHistory> findById(String id) {
        return Optional.ofNullable(couponHistories.get(id));
    }
    
    @Override
    public List<CouponHistory> findByUserId(String userId, int limit, int offset) {
        // TODO: 사용자별 쿠폰 히스토리 조회 로직 구현
        return new ArrayList<>();
    }
    
    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        couponHistories.put(couponHistory.getId(), couponHistory);
        return couponHistory;
    }
    
    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        // TODO: 사용자-쿠폰 조합 존재 여부 확인 로직 구현
        return false;
    }
} 