package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderWithDetailsUseCase;
import kr.hhplus.be.server.domain.usecase.order.ValidateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CompleteOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CreatePaymentUseCase;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 주문 생성, 조회, 결제 등의 기능을 제공하며,
 * 복잡한 비즈니스 플로우를 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetOrderListUseCase getOrderListUseCase;
    private final GetOrderWithDetailsUseCase getOrderWithDetailsUseCase;
    private final ValidateOrderUseCase validateOrderUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final CreatePaymentUseCase createPaymentUseCase;
    private final DeductBalanceUseCase deductBalanceUseCase;
    private final ApplyCouponUseCase applyCouponUseCase;
    private final LockingPort lockingPort;
    private final UserRepositoryPort userRepositoryPort;

    /**
     * 주문 생성
     * 
     * 동시성 제어를 위해 분산 락을 사용합니다.
     * 
     * @param userId 사용자 ID
     * @param productQuantities 상품 및 수량 정보
     * @return 생성된 주문 정보
     */
    @Transactional
    public Order createOrder(Long userId, List<ProductQuantityDto> productQuantities) {
        String lockKey = "order-creation-" + userId;
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return createOrderUseCase.execute(userId, productQuantities);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }

    /**
     * 주문 조회
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 주문 정보
     */
    public Order getOrder(Long orderId, Long userId) {
        return getOrderUseCase.execute(orderId, userId);
    }

    /**
     * 사용자 주문 목록 조회
     * 
     * @param userId 사용자 ID
     * @param limit 조회할 주문 개수
     * @param offset 건너뛸 주문 개수
     * @return 주문 목록
     */
    public List<Order> getOrderList(Long userId, int limit, int offset) {
        return getOrderListUseCase.execute(userId, limit, offset);
    }

    /**
     * 주문 상세 정보 조회 (주문 항목 포함)
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 상세 주문 정보
     */
    public Order getOrderWithDetails(Long orderId, Long userId) {
        return getOrderWithDetailsUseCase.execute(orderId, userId);
    }

    /**
     * 주문 결제 처리
     * 
     * 여러 UseCase를 조합하여 결제 프로세스를 수행합니다:
     * 1. 주문 검증
     * 2. 쿠폰 적용
     * 3. 잔액 차감
     * 4. 주문 완료 처리
     * 5. 결제 생성
     * 
     * 동시성 제어를 위해 분산 락을 사용합니다.
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (선택사항)
     * @return 결제 정보
     */
    @Transactional
    public Payment payOrder(Long orderId, Long userId, Long couponId) {
        String paymentLockKey = "payment-" + orderId;
        String balanceLockKey = "balance-" + userId;
        
        // 락 획득 (데드락 방지를 위해 순서 고정)
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
            // 락 해제 (획득 순서의 반대로 해제)
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
}