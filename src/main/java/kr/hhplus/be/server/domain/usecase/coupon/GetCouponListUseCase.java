package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
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
    
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset);
    }
} 