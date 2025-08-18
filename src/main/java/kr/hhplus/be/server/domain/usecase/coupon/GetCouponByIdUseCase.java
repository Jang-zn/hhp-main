package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 ID로 쿠폰 정보를 조회하는 UseCase
 */
@Component
@RequiredArgsConstructor
public class GetCouponByIdUseCase {
    
    private final CouponRepositoryPort couponRepositoryPort;
    
    /**
     * 쿠폰 ID로 쿠폰 정보 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 정보
     * @throws CouponException.NotFound 쿠폰을 찾을 수 없는 경우
     */
    public Coupon execute(Long couponId) {
        return couponRepositoryPort.findById(couponId)
                .orElseThrow(() -> new CouponException.NotFound());
    }
}