package kr.hhplus.be.server.domain.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
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
            
            List<CouponHistory> result = couponHistoryRepositoryPort.findByUserIdWithPagination(userId, limit, offset);
            
            log.debug("쿠폰 목록 조회 완료: userId={}, count={}", userId, result.size());
            
            return result;
            
        } catch (UserException e) {
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
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT);
        }
    }
    
} 