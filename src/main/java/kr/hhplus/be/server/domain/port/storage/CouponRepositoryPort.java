package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepositoryPort {
    /**
 * Retrieves a coupon by its unique identifier.
 *
 * @param id the unique identifier of the coupon
 * @return an {@code Optional} containing the coupon if found, or empty if not found
 */
Optional<Coupon> findById(Long id);
    /**
 * Persists a new coupon entity and returns the saved instance.
 *
 * @param coupon the coupon to be saved
 * @return the saved coupon entity
 */
Coupon save(Coupon coupon);
    /**
 * Updates the issued count of the coupon identified by the given ID.
 *
 * @param couponId    the unique identifier of the coupon to update
 * @param issuedCount the new issued count to set for the coupon
 * @return the updated Coupon entity with the new issued count
 */
Coupon updateIssuedCount(Long couponId, int issuedCount);
    /**
 * Retrieves a list of coupons applicable to products associated with the specified coupon ID.
 *
 * @param couponId the identifier of the coupon whose applicable products are to be found
 * @return a list of coupons applicable to the related products
 */
List<Coupon> findApplicableProducts(Long couponId);
} 