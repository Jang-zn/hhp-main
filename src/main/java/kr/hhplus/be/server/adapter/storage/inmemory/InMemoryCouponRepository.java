package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCouponRepository implements CouponRepositoryPort {
    
    private final Map<Long, Coupon> coupons = new ConcurrentHashMap<>();
    
    /**
     * Retrieves a coupon by its ID from the in-memory store.
     *
     * @param id the unique identifier of the coupon
     * @return an {@code Optional} containing the coupon if found, or empty if not present
     */
    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(coupons.get(id));
    }
    
    /**
     * Stores or updates the given coupon in the in-memory repository.
     *
     * @param coupon the coupon to be saved or updated
     * @return the saved coupon
     */
    @Override
    public Coupon save(Coupon coupon) {
        coupons.put(coupon.getId(), coupon);
        return coupon;
    }
    
    /**
     * Retrieves a coupon by its ID and is intended to update its issued count, but currently returns the coupon without modification.
     *
     * @param couponId the ID of the coupon to retrieve
     * @param issuedCount the new issued count (not currently applied)
     * @return the coupon associated with the given ID, or {@code null} if not found
     */
    @Override
    public Coupon updateIssuedCount(Long couponId, int issuedCount) {
        Coupon coupon = coupons.get(couponId);
        if (coupon != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return coupon;
    }
    
    /**
     * Returns a list of products applicable to the specified coupon.
     *
     * Currently returns an empty list as the implementation is not provided.
     *
     * @param couponId the ID of the coupon
     * @return a list of applicable products for the coupon, or an empty list if none are found or not implemented
     */
    @Override
    public List<Coupon> findApplicableProducts(Long couponId) {
        // TODO: 적용 가능한 상품 조회 로직 구현
        return new ArrayList<>();
    }
} 