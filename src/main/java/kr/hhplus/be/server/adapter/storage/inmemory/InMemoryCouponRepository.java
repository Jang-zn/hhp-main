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
    
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Coupon> findById(String id) {
        return Optional.ofNullable(coupons.get(id));
    }
    
    @Override
    public Coupon save(Coupon coupon) {
        coupons.put(coupon.getId(), coupon);
        return coupon;
    }
    
    @Override
    public Coupon updateIssuedCount(String couponId, int issuedCount) {
        Coupon coupon = coupons.get(couponId);
        if (coupon != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return coupon;
    }
    
    @Override
    public List<Coupon> findApplicableProducts(String couponId) {
        // TODO: 적용 가능한 상품 조회 로직 구현
        return new ArrayList<>();
    }
} 