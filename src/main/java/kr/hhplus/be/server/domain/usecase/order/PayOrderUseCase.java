package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PayOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final CouponRepositoryPort couponRepositoryPort;
    private final EventLogRepositoryPort eventLogRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    private final MessagingPort messagingPort;
    
    public Payment execute(Long orderId, Long userId, Long couponId) {
        String lockKey = "payment-" + orderId;
        if (!lockingPort.acquireLock(lockKey)) {
            throw new RuntimeException("Failed to acquire lock");
        }
        try {
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Order order = orderRepositoryPort.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            BigDecimal finalAmount = order.getTotalAmount();
            if (couponId != null) {
                Coupon coupon = couponRepositoryPort.findById(couponId)
                        .orElseThrow(() -> new RuntimeException("Coupon not found"));
                finalAmount = finalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
            }

            Balance balance = balanceRepositoryPort.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Balance not found"));
            balance.subtractAmount(finalAmount);
            balanceRepositoryPort.save(balance);

            Payment payment = Payment.builder()
                    .order(order)
                    .user(user)
                    .amount(finalAmount)
                    .status(PaymentStatus.PAID)
                    .build();

            return paymentRepositoryPort.save(payment);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
} 