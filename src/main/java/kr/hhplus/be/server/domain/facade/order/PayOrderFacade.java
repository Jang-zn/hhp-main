package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.usecase.order.ValidateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CreatePaymentUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class PayOrderFacade {

    private final ValidateOrderUseCase validateOrderUseCase;
    private final DeductBalanceUseCase deductBalanceUseCase;
    private final ApplyCouponUseCase applyCouponUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final CreatePaymentUseCase createPaymentUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;

    public PayOrderFacade(
            ValidateOrderUseCase validateOrderUseCase,
            DeductBalanceUseCase deductBalanceUseCase,
            ApplyCouponUseCase applyCouponUseCase,
            CompleteOrderUseCase completeOrderUseCase,
            CreatePaymentUseCase createPaymentUseCase,
            LockingPort lockingPort,
            UserRepositoryPort userRepositoryPort
    ) {
        this.validateOrderUseCase = validateOrderUseCase;
        this.deductBalanceUseCase = deductBalanceUseCase;
        this.applyCouponUseCase = applyCouponUseCase;
        this.completeOrderUseCase = completeOrderUseCase;
        this.createPaymentUseCase = createPaymentUseCase;
        this.lockingPort = lockingPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    @Transactional
    public Payment payOrder(Long orderId, Long userId, Long couponId) {
        String paymentLockKey = "payment-" + orderId;
        String balanceLockKey = "balance-" + userId;
        
        // 락 획득
        if (!lockingPort.acquireLock(paymentLockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        if (!lockingPort.acquireLock(balanceLockKey)) {
            lockingPort.releaseLock(paymentLockKey);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 1. 사용자 존재 확인
            if (!userRepositoryPort.existsById(userId)) {
                throw new UserException.NotFound();
            }
            
            // 2. 주문 검증
            Order order = validateOrderUseCase.execute(orderId, userId);
            
            // 3. 쿠폰 적용 (선택사항)
            BigDecimal finalAmount = applyCouponUseCase.execute(order.getTotalAmount(), couponId);
            
            // 4. 잔액 차감
            deductBalanceUseCase.execute(userId, finalAmount);
            
            // 5. 주문 완료 처리
            completeOrderUseCase.execute(order);
            
            // 6. 결제 생성
            return createPaymentUseCase.execute(order.getId(), userId, finalAmount);
            
        } finally {
            // 락 해제
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
}