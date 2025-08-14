package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    
    public Optional<Order> execute(Long userId, Long orderId) {
        log.debug("주문 조회 요청: userId={}, orderId={}", userId, orderId);
        
        // 파라미터 검증
        validateParameters(userId, orderId);
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("존재하지 않는 사용자: userId={}", userId);
            throw new UserException.NotFound();
        }
        
        // 먼저 주문이 존재하는지 확인
        Optional<Order> orderOpt = orderRepositoryPort.findById(orderId);
        
        if (orderOpt.isEmpty()) {
            log.debug("주문이 존재하지 않음: orderId={}", orderId);
            return Optional.empty(); // OrderService에서 OrderException.NotFound(404)로 변환됨
        }
        
        Order order = orderOpt.get();
        
        // 주문이 해당 사용자의 것인지 확인
        if (!order.getUserId().equals(userId)) {
            log.warn("주문 접근 권한 없음: orderId={}, requestUserId={}, orderUserId={}", 
                orderId, userId, order.getUserId());
            throw new OrderException.Unauthorized(); // 403 Forbidden
        }
        
        log.debug("주문 조회 성공: userId={}, orderId={}", userId, orderId);
        return Optional.of(order);
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