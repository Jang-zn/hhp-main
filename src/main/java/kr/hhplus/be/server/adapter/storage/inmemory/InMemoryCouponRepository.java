package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCouponRepository implements CouponRepositoryPort {

    private final Map<Long, Coupon> coupons = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    @Override
    public Optional<Coupon> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
        }
        return Optional.ofNullable(coupons.get(id));
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon == null) {
            throw new IllegalArgumentException("Coupon cannot be null");
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long couponId = coupon.getId() != null ? coupon.getId() : nextId.getAndIncrement();
        
        Coupon savedCoupon = coupons.compute(couponId, (key, existingCoupon) -> {
            if (existingCoupon != null) {
                // 기존 쿠폰 업데이트
                return Coupon.builder()
                        .id(existingCoupon.getId())
                        .code(coupon.getCode())
                        .discountRate(coupon.getDiscountRate())
                        .maxIssuance(coupon.getMaxIssuance())
                        .issuedCount(coupon.getIssuedCount())
                        .startDate(coupon.getStartDate())
                        .endDate(coupon.getEndDate())
                        .product(coupon.getProduct())
                        .createdAt(existingCoupon.getCreatedAt())
                        .updatedAt(coupon.getUpdatedAt())
                        .build();
            } else {
                // 새로운 쿠폰 생성
                return Coupon.builder()
                        .id(couponId)
                        .code(coupon.getCode())
                        .discountRate(coupon.getDiscountRate())
                        .maxIssuance(coupon.getMaxIssuance())
                        .issuedCount(coupon.getIssuedCount())
                        .startDate(coupon.getStartDate())
                        .endDate(coupon.getEndDate())
                        .product(coupon.getProduct())
                        .createdAt(coupon.getCreatedAt())
                        .updatedAt(coupon.getUpdatedAt())
                        .build();
            }
        });
        
        return savedCoupon;
    }
} 