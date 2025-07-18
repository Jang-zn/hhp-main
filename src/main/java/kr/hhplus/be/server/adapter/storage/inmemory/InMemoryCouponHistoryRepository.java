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
    
    /**
     * Retrieves a CouponHistory entity by its ID.
     *
     * @param id the unique identifier of the CouponHistory
     * @return an Optional containing the CouponHistory if found, or empty if not present
     */
    @Override
    public Optional<CouponHistory> findById(Long id) {
        return Optional.ofNullable(couponHistories.get(id));
    }
    
    /**
     * Returns a list of coupon histories for the specified user with pagination.
     *
     * @param userId the ID of the user whose coupon histories are to be retrieved
     * @param limit the maximum number of results to return
     * @param offset the starting index for pagination
     * @return a list of coupon histories for the user; currently always returns an empty list
     */
    @Override
    public List<CouponHistory> findByUserId(Long userId, int limit, int offset) {
        // TODO: 사용자별 쿠폰 히스토리 조회 로직 구현
        return new ArrayList<>();
    }
    
    /**
     * Saves or updates the given CouponHistory in the in-memory repository.
     *
     * @param couponHistory the CouponHistory entity to be saved or updated
     * @return the saved CouponHistory entity
     */
    @Override
    public CouponHistory save(CouponHistory couponHistory) {
        couponHistories.put(couponHistory.getId(), couponHistory);
        return couponHistory;
    }
    
    /**
     * Checks if a coupon history exists for the specified user and coupon combination.
     *
     * @param userId the ID of the user
     * @param couponId the ID of the coupon
     * @return always returns {@code false} as this method is not yet implemented
     */
    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        // TODO: 사용자-쿠폰 조합 존재 여부 확인 로직 구현
        return false;
    }
} 