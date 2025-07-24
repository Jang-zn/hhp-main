package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final CachePort cachePort;
    
    @Transactional(readOnly = true)
    public Optional<Order> execute(Long userId, Long orderId) {
        log.debug("주문 조회 요청: userId={}, orderId={}", userId, orderId);
        
        // 파라미터 검증
        validateParameters(userId, orderId);
        
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 사용자: userId={}", userId);
                    return new UserException.NotFound();
                });
        
        try {
            // 캐시에서 주문 조회 시도
            String cacheKey = "order_" + orderId + "_" + userId;
            Optional<Order> result = cachePort.get(cacheKey, Optional.class, () -> 
                orderRepositoryPort.findByIdAndUser(orderId, user)
            );
            
            if (result != null && result.isPresent()) {
                log.debug("주문 조회 성공: userId={}, orderId={}", userId, orderId);
            } else {
                log.debug("주문 조회 결과 없음: userId={}, orderId={}", userId, orderId);
            }
            
            return result != null ? result : Optional.empty();
        } catch (Exception e) {
            log.error("주문 조회 중 오류 발생: userId={}, orderId={}", userId, orderId, e);
            // 캐시 오류 시 DB에서 직접 조회
            return orderRepositoryPort.findByIdAndUser(orderId, user);
        }
    }
    
    private void validateParameters(Long userId, Long orderId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
    }
} 