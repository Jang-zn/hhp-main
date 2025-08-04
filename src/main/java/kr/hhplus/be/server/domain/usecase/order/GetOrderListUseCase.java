package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final CachePort cachePort;
    
    public List<Order> execute(Long userId) {
        log.debug("주문 목록 조회 요청: userId={}", userId);
        
        // 파라미터 검증
        validateParameters(userId);
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("존재하지 않는 사용자: userId={}", userId);
            throw new UserException.NotFound();
        }
        
        try {
            // 캐시에서 주문 목록 조회 시도
            String cacheKey = "user_orders_" + userId;
            List<Order> cachedOrders = cachePort.get(cacheKey, List.class, () -> 
                orderRepositoryPort.findByUserId(userId)
            );
            
            if (cachedOrders != null) {
                log.debug("주문 목록 조회 성공: userId={}, count={}", userId, cachedOrders.size());
                return cachedOrders;
            } else {
                log.debug("주문 목록 조회 결과 없음: userId={}", userId);
                return List.of();
            }
        } catch (Exception e) {
            log.error("주문 목록 조회 중 오류 발생: userId={}", userId, e);
            // 캐시 오류 시 DB에서 직접 조회
            return orderRepositoryPort.findByUserId(userId);
        }
    }
    
    private void validateParameters(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }
    }
} 