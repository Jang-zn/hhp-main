package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetCouponListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final CachePort cachePort;
    
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String cacheKey = "coupon_list_" + userId + "_" + limit + "_" + offset;
        
        return cachePort.get(cacheKey, List.class, () -> 
            couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset)
        );
    }
} 