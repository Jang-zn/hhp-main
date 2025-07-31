package kr.hhplus.be.server.domain.facade.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GetCouponListFacade {

    private final GetCouponListUseCase getCouponListUseCase;

    public GetCouponListFacade(GetCouponListUseCase getCouponListUseCase) {
        this.getCouponListUseCase = getCouponListUseCase;
    }

    @Transactional(readOnly = true)
    public List<CouponHistory> getCouponList(Long userId, int limit, int offset) {
        return getCouponListUseCase.execute(userId, limit, offset);
    }
}