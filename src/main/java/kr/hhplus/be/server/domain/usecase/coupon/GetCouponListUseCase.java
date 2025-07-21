package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetCouponListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    private final CachePort cachePort;
    
    private static final int MAX_LIMIT = 1000;
    private static final int CACHE_TTL_SECONDS = 300;
    
    public List<CouponHistory> execute(Long userId, int limit, int offset) {
        log.debug("쿠폰 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        // 입력 값 검증
        validateInputs(userId, limit, offset);
        
        try {
            // 사용자 조회
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("사용자 없음: userId={}", userId);
                        return new IllegalArgumentException("User not found");
                    });
            
            // 캐시 키 생성
            String cacheKey = "coupon_list_" + userId + "_" + limit + "_" + offset;
            
            // 캐시에서 조회 시도
            List<CouponHistory> result = getCachedCouponList(cacheKey, user, limit, offset);
            
            log.debug("쿠폰 목록 조회 완료: userId={}, count={}", userId, result.size());
            
            return result;
            
        } catch (IllegalArgumentException e) {
            log.error("쿠폰 목록 조회 실패: userId={}, limit={}, offset={}, error={}", 
                    userId, limit, offset, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 목록 조회 중 예상치 못한 오류: userId={}, limit={}, offset={}", 
                    userId, limit, offset, e);
            throw new IllegalArgumentException("Failed to retrieve coupon list");
        }
    }
    
    private void validateInputs(Long userId, int limit, int offset) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
    }
    
    private List<CouponHistory> getCachedCouponList(String cacheKey, User user, int limit, int offset) {
        try {
            return cachePort.get(cacheKey, List.class, () -> {
                List<CouponHistory> couponHistories = couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset);
                log.debug("데이터베이스에서 쿠폰 목록 조회: userId={}, count={}", user.getId(), couponHistories.size());
                return couponHistories;
            });
        } catch (Exception e) {
            log.warn("캐시 조회 실패, 직접 DB 조회: userId={}, error={}", user.getId(), e.getMessage());
            // 캐시 실패 시 직접 DB에서 조회
            return couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset);
        }
    }
} 