package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateOrderUseCase {
    
    private final OrderRepositoryPort orderRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    
    public Order execute(Long orderId, Long userId) {
        log.debug("주문 유효성 검증: orderId={}, userId={}", orderId, userId);
        
        // 주문 조회
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 주문: orderId={}", orderId);
                    return new OrderException.NotFound();
                });

        // 주문 소유권 검증
        if (!order.getUserId().equals(userId)) {
            log.warn("주문 소유권 불일치: orderId={}, userId={}, orderUserId={}", 
                    orderId, userId, order.getUserId());
            throw new OrderException.Unauthorized();
        }

        // 주문 상태 검증 (이미 결제된 주문인지 확인)
        List<Payment> existingPayments = paymentRepositoryPort.findByOrderId(orderId);
        if (!existingPayments.isEmpty()) {
            log.warn("이미 결제된 주문: orderId={}", orderId);
            throw new OrderException.AlreadyPaid();
        }
        
        log.debug("주문 유효성 검증 완료: orderId={}", orderId);
        return order;
    }
}