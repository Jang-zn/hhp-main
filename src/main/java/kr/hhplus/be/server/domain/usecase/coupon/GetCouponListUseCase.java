package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetCouponListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    /**
     * Retrieves a paginated list of coupon histories for the specified user.
     *
     * @param userId the ID of the user whose coupon histories are to be retrieved
     * @param limit the maximum number of coupon histories to return
     * @param offset the starting index for pagination
     * @return a list of CouponHistory objects representing the user's coupons; currently returns an empty list
     */
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        // TODO: 보유 쿠폰 목록 조회 로직 구현
        return List.of();
    }
} 