package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.CouponHistory;

import java.util.List;
import java.util.Optional;

public interface CouponHistoryRepositoryPort {
    /**
 * Retrieves a coupon history record by its unique identifier.
 *
 * @param id the unique identifier of the coupon history record
 * @return an {@code Optional} containing the found {@code CouponHistory}, or empty if not found
 */
Optional<CouponHistory> findById(Long id);
    /**
 * Retrieves a paginated list of coupon history records for the specified user.
 *
 * @param userId the unique identifier of the user whose coupon history is to be retrieved
 * @param limit the maximum number of records to return
 * @param offset the starting index from which to retrieve records
 * @return a list of coupon history records for the user, limited and offset as specified
 */
List<CouponHistory> findByUserId(Long userId, int limit, int offset);
    /**
 * Persists the given CouponHistory entity and returns the saved instance.
 *
 * @param couponHistory the CouponHistory entity to be saved
 * @return the persisted CouponHistory entity
 */
CouponHistory save(CouponHistory couponHistory);
    /**
 * Checks if a coupon history record exists for the specified user ID and coupon ID.
 *
 * @param userId   the unique identifier of the user
 * @param couponId the unique identifier of the coupon
 * @return {@code true} if a matching coupon history record exists; {@code false} otherwise
 */
boolean existsByUserIdAndCouponId(Long userId, Long couponId);
} 