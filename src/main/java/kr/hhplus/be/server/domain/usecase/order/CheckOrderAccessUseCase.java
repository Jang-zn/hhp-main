package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckOrderAccessUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    
    @Transactional(readOnly = true)
    public Order execute(Long userId, Long orderId) {
        log.debug("주문 접근 권한 체크: userId={}, orderId={}", userId, orderId);
        
        // 파라미터 검증
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 사용자: userId={}", userId);
                    return new UserException.NotFound();
                });
        
        // 주문이 존재하는지 먼저 확인
        boolean orderExists = orderRepositoryPort.findById(orderId).isPresent();
        if (!orderExists) {
            log.warn("존재하지 않는 주문: orderId={}", orderId);
            throw new OrderException.NotFound();
        }
        
        // 해당 사용자의 주문인지 확인
        Optional<Order> orderOpt = orderRepositoryPort.findByIdAndUser(orderId, user);
        if (orderOpt.isEmpty()) {
            log.warn("주문 접근 권한 없음: userId={}, orderId={}", userId, orderId);
            throw new OrderException.Unauthorized();
        }
        
        Order order = orderOpt.get();
        log.debug("주문 접근 권한 확인 완료: userId={}, orderId={}", userId, orderId);
        return order;
    }
}