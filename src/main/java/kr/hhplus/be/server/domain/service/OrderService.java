package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
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
import org.springframework.transaction.support.TransactionTemplate;

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
public class OrderService {

    private final TransactionTemplate transactionTemplate;
    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetOrderListUseCase getOrderListUseCase;
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
     * 동시성 제어를 위해 분산 락을 사용하고, TransactionTemplate으로 명시적 트랜잭션 관리합니다.
     * 실행 순서: Lock 획득 → Transaction 시작 → Logic 실행 → Transaction 종료 → Lock 해제
     * 
     * @param userId 사용자 ID
     * @param productQuantities 상품 및 수량 정보
     * @return 생성된 주문 정보
     */
    public Order createOrder(Long userId, List<ProductQuantityDto> productQuantities) {
        String lockKey = "order-creation-" + userId;
        
        // 1. 락 획득
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 2. 명시적 트랜잭션 실행
            return transactionTemplate.execute(status -> {
                // 3. 비즈니스 로직 실행 (트랜잭션 내)
                return createOrderUseCase.execute(userId, productQuantities);
            });
        } finally {
            // 4. 락 해제
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
        return getOrderUseCase.execute(userId, orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
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
        return getOrderListUseCase.execute(userId);
    }

    /**
     * 주문 상세 정보 조회 (주문 항목 포함)
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 상세 주문 정보
     */
    public Order getOrderWithDetails(Long orderId, Long userId) {
        return getOrderUseCase.execute(userId, orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    /**
     * 주문 결제 처리
     * 
     * 여러 UseCase를 조합하여 결제 프로세스를 수행합니다:
     * 1. 사용자 존재 확인
     * 2. 주문 검증
     * 3. 쿠폰 적용
     * 4. 잔액 차감
     * 5. 주문 완료 처리
     * 6. 결제 생성
     * 
     * 동시성 제어를 위해 분산 락을 사용하고, TransactionTemplate으로 명시적 트랜잭션 관리합니다.
     * 실행 순서: Lock 획득 → Transaction 시작 → Logic 실행 → Transaction 종료 → Lock 해제
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (선택사항)
     * @return 결제 정보
     */
    public Payment payOrder(Long orderId, Long userId, Long couponId) {
        String paymentLockKey = "payment-" + orderId;
        String balanceLockKey = "balance-" + userId;
        
        // 1. 락 획득 (데드락 방지를 위해 순서 고정)
        if (!lockingPort.acquireLock(paymentLockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        if (!lockingPort.acquireLock(balanceLockKey)) {
            lockingPort.releaseLock(paymentLockKey);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 2. 명시적 트랜잭션 실행
            return transactionTemplate.execute(status -> {
                // 3. 비즈니스 로직 실행 (트랜잭션 내)
                
                // 3-1. 사용자 존재 확인
                if (!userRepositoryPort.existsById(userId)) {
                    throw new UserException.NotFound();
                }
                
                // 3-2. 주문 검증
                Order order = validateOrderUseCase.execute(orderId, userId);
                
                // 3-3. 쿠폰 적용 (선택사항)
                BigDecimal finalAmount = applyCouponUseCase.execute(order.getTotalAmount(), couponId);
                
                // 3-4. 잔액 차감
                deductBalanceUseCase.execute(userId, finalAmount);
                
                // 3-5. 주문 완료 처리
                completeOrderUseCase.execute(order);
                
                // 3-6. 결제 생성
                return createPaymentUseCase.execute(order.getId(), userId, finalAmount);
            });
            
        } finally {
            // 4. 락 해제 (획득 순서의 반대로 해제)
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
}