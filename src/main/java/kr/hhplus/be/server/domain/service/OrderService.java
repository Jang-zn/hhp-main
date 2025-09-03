package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.common.util.KeyGenerator;
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
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.enums.EventTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import kr.hhplus.be.server.domain.port.event.EventPort;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 관련 비즈니스 로직을 처리하는 서비스
 * 
 * UseCase 레이어에 위임하여 주문 생성, 조회, 결제 등의 기능을 제공하며,
 * 복잡한 비즈니스 플로우를 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final KeyGenerator keyGenerator;
    private final EventPort eventPort;
    

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
        log.debug("주문 생성 요청: userId={}, productCount={}", userId, productQuantities.size());
        
        Long[] productIds = productQuantities.stream()
            .map(ProductQuantityDto::getProductId)
            .sorted()
            .toArray(Long[]::new);
        
        String lockKey = keyGenerator.generateOrderCreateMultiProductKey(userId, productIds);
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            Order result = transactionTemplate.execute(status -> {
                return createOrderUseCase.execute(userId, productQuantities);
            });
            
            log.info("주문 생성 완료: orderId={}, userId={}", result.getId(), userId);
            return result;
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
        log.debug("주문 조회 요청: orderId={}, userId={}", orderId, userId);
        
        return getOrderUseCase.execute(userId, orderId)
            .orElseThrow(() -> {
                log.warn("존재하지 않는 주문 또는 접근 권한 없음: orderId={}, userId={}", orderId, userId);
                return new OrderException.NotFound();
            });
    }
    /**
     * 사용자 주문 목록 조회 (페이징 지원)
     * 
     * @param userId 사용자 ID
     * @param limit 페이지 크기
     * @param offset 페이지 오프셋
     * @return 주문 목록
     */
    public List<Order> getOrderList(Long userId, int limit, int offset) {
        log.debug("주문 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("존재하지 않는 사용자: userId={}", userId);
            throw new UserException.NotFound();
        }
        
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
        log.debug("주문 상세 정보 조회 요청: orderId={}, userId={}", orderId, userId);
        
        return getOrderUseCase.execute(userId, orderId)
            .orElseThrow(() -> {
                log.warn("존재하지 않는 주문 또는 접근 권한 없음: orderId={}, userId={}", orderId, userId);
                return new OrderException.NotFound();
            });
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
        String paymentLockKey = keyGenerator.generateOrderPaymentKey(orderId);
        String balanceLockKey = keyGenerator.generateBalanceKey(userId);
        
        if (!lockingPort.acquireLock(paymentLockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        if (!lockingPort.acquireLock(balanceLockKey)) {
            lockingPort.releaseLock(paymentLockKey);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            Payment result = transactionTemplate.execute(status -> {
                if (!userRepositoryPort.existsById(userId)) {
                    throw new UserException.NotFound();
                }
                
                Order order = validateOrderUseCase.execute(orderId, userId);
                BigDecimal finalAmount = applyCouponUseCase.execute(order.getTotalAmount(), couponId);
                deductBalanceUseCase.execute(userId, finalAmount);
                completeOrderUseCase.execute(order);
                
                return createPaymentUseCase.execute(order.getId(), userId, finalAmount);
            });
            
            log.info("주문 결제 완료: orderId={}, userId={}, amount={}", orderId, userId, result.getAmount());
            return result;
            
        } finally {
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
    
    
}