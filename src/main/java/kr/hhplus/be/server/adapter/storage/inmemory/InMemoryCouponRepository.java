package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.api.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
@Profile("test_inmemory")
public class InMemoryCouponRepository implements CouponRepositoryPort {

    private final Map<Long, Coupon> coupons = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    public void clear() {
        coupons.clear();
        nextId.set(1L);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        if (id == null) {
            throw new CouponException.CouponIdCannotBeNull();
        }
        return Optional.ofNullable(coupons.get(id));
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon == null) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (coupon.getCode() == null) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (coupon.getDiscountRate() == null 
            || coupon.getDiscountRate().compareTo(BigDecimal.ZERO) < 0 
            || coupon.getDiscountRate().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (coupon.getMaxIssuance() < 0 || coupon.getMaxIssuance() < coupon.getIssuedCount()) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (coupon.getStartDate() == null || coupon.getEndDate() == null) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (coupon.getStartDate().isAfter(coupon.getEndDate())) {
            throw new CouponException.InvalidCouponData(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        // ConcurrentHashMap의 compute를 사용하여 원자적 업데이트
        Long couponId = coupon.getId() != null ? coupon.getId() : nextId.getAndIncrement();
        
        Coupon savedCoupon = coupons.compute(couponId, (key, existingCoupon) -> {
            if (existingCoupon != null) {
                coupon.onUpdate();
                coupon.setId(existingCoupon.getId());
                coupon.setCreatedAt(existingCoupon.getCreatedAt());
                return coupon;
            } else {
                coupon.onCreate();
                if (coupon.getId() == null) {
                    coupon.setId(couponId);
                }
                return coupon;
            }
        });
        
        return savedCoupon;
    }

    @Override
    public List<Coupon> findByStatus(CouponStatus status) {
        return coupons.values().stream()
                .filter(coupon -> coupon.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Coupon> findExpiredCouponsNotInStatus(LocalDateTime now, CouponStatus... excludeStatuses) {
        List<CouponStatus> excludeList = Arrays.asList(excludeStatuses);
        
        return coupons.values().stream()
                .filter(coupon -> now.isAfter(coupon.getEndDate()))
                .filter(coupon -> !excludeList.contains(coupon.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public long countByStatus(CouponStatus status) {
        return coupons.values().stream()
                .filter(coupon -> coupon.getStatus() == status)
                .count();
    }

    @Override
    public Optional<Coupon> findByIdWithLock(Long id) {
        // InMemory 환경에서는 별도의 락 구현 없이 일반 조회와 동일하게 처리
        return findById(id);
    }
} 