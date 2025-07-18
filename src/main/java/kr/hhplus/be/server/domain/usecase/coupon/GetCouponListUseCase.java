package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetCouponListUseCase {
    
    private final StoragePort storagePort;
    
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        // TODO: 보유 쿠폰 목록 조회 로직 구현
        return List.of();
    }
} 