package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetCouponListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    private static final int MAX_LIMIT = 1000;
    
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        log.debug("쿠폰 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        // 입력 값 검증
        validateInputs(userId, limit, offset);
        
        try {
            // 사용자 존재 확인
            if (!userRepositoryPort.existsById(userId)) {
                log.warn("사용자 없음: userId={}", userId);
                throw new UserException.NotFound();
            }
            
            String cacheKey = keyGenerator.generateCouponListCacheKey(userId, limit, offset);
            
            // 캐시에서 조회 시도
            @SuppressWarnings("unchecked")
            List<CouponHistory> cachedCoupons = cachePort.get(cacheKey, List.class);
            
            if (cachedCoupons != null) {
                log.debug("캐시에서 쿠폰 목록 조회 성공: userId={}, count={}", userId, cachedCoupons.size());
                return cachedCoupons;
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            PageRequest pageable = PageRequest.of(offset / limit, limit);
            List<CouponHistory> result = couponHistoryRepositoryPort.findByUserIdWithPagination(userId, pageable);
            
            if (!result.isEmpty()) {
                log.debug("데이터베이스에서 쿠폰 목록 조회: userId={}, count={}", userId, result.size());
                
                // 캐시에 저장
                cachePort.put(cacheKey, result, CacheTTL.USER_COUPON_LIST.getSeconds());
            } else {
                log.debug("쿠폰 목록 조회 결과 없음: userId={}", userId);
            }
            
            return result;
            
        } catch (UserException e) {
            log.error("쿠폰 목록 조회 실패: userId={}, limit={}, offset={}, error={}", 
                    userId, limit, offset, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 목록 조회 중 예상치 못한 오류: userId={}, limit={}, offset={}", 
                    userId, limit, offset, e);
            // 캐시 오류 시 직접 DB에서 조회
            PageRequest pageable = PageRequest.of(offset / limit, limit);
            return couponHistoryRepositoryPort.findByUserIdWithPagination(userId, pageable);
        }
    }
    
    private void validateInputs(Long userId, int limit, int offset) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT);
        }
    }
    
} 