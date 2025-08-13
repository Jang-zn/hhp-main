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
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.service.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CachePort cachePort;
    private final KeyGenerator lockKeyGenerator;
    

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
        Long[] productIds = productQuantities.stream()
            .map(ProductQuantityDto::getProductId)
            .sorted()
            .toArray(Long[]::new);
        
        String lockKey = lockKeyGenerator.generateOrderCreateMultiProductKey(userId, productIds);
        
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            Order order = transactionTemplate.execute(status -> {
                return createOrderUseCase.execute(userId, productQuantities);
            });
            
            // 트랜잭션 커밋 후 캐시 무효화
            invalidateUserRelatedCache(userId);
            
            return order;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }

    /**
     * 주문 조회 (캐시 적용)
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 주문 정보
     */
    public Order getOrder(Long orderId, Long userId) {
        log.debug("주문 조회 요청: orderId={}, userId={}", orderId, userId);
        
        try {
            String cacheKey = lockKeyGenerator.generateOrderCacheKey(orderId);
            Order cachedOrder = cachePort.get(cacheKey, Order.class, () -> {
                return getOrderUseCase.execute(userId, orderId).orElse(null);
            });
            
            if (cachedOrder != null) {
                log.debug("주문 조회 성공: orderId={}, userId={}", orderId, userId);
                return cachedOrder;
            } else {
                throw new RuntimeException("Order not found");
            }
        } catch (Exception e) {
            log.error("주문 조회 중 오류 발생: orderId={}, userId={}", orderId, userId, e);
            return getOrderUseCase.execute(userId, orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
        }
    }

    /**
     * 사용자 주문 목록 조회 (캐시 적용)
     * 
     * @param userId 사용자 ID
     * @return 주문 목록
     */
    public List<Order> getOrderList(Long userId) {
        log.debug("주문 목록 조회 요청: userId={}", userId);
        
        try {
            String cacheKey = lockKeyGenerator.generateOrderListCacheKey(userId, 50, 0);
            return cachePort.getList(cacheKey, () -> {
                List<Order> orders = getOrderListUseCase.execute(userId);
                log.debug("데이터베이스에서 주문 목록 조회: userId={}, count={}", userId, orders.size());
                return orders;
            });
        } catch (Exception e) {
            log.error("주문 목록 조회 중 오류 발생: userId={}", userId, e);
            return getOrderListUseCase.execute(userId);
        }
    }
    
    /**
     * 사용자 주문 목록 조회 (페이징 지원, 캐시 적용)
     * 
     * @param userId 사용자 ID
     * @param limit 페이지 크기
     * @param offset 페이지 오프셋
     * @return 주문 목록
     */
    public List<Order> getOrderList(Long userId, int limit, int offset) {
        log.debug("주문 목록 조회 요청 (페이징): userId={}, limit={}, offset={}", userId, limit, offset);
        
        try {
            String cacheKey = lockKeyGenerator.generateOrderListCacheKey(userId, limit, offset);
            return cachePort.getList(cacheKey, () -> {
                // TODO: UseCase에서 limit, offset 지원하도록 수정 필요
                List<Order> allOrders = getOrderListUseCase.execute(userId);
                
                // 임시로 메모리에서 페이징 처리 (성능상 비효율적, 추후 개선 필요)
                int fromIndex = Math.min(offset, allOrders.size());
                int toIndex = Math.min(offset + limit, allOrders.size());
                List<Order> paginatedOrders = allOrders.subList(fromIndex, toIndex);
                
                log.debug("데이터베이스에서 주문 목록 조회 (페이징): userId={}, total={}, returned={}", 
                    userId, allOrders.size(), paginatedOrders.size());
                return paginatedOrders;
            });
        } catch (Exception e) {
            log.error("주문 목록 조회 중 오류 발생 (페이징): userId={}, limit={}, offset={}", userId, limit, offset, e);
            
            // 예외 발생 시 전체 목록에서 페이징 처리
            List<Order> allOrders = getOrderListUseCase.execute(userId);
            int fromIndex = Math.min(offset, allOrders.size());
            int toIndex = Math.min(offset + limit, allOrders.size());
            return allOrders.subList(fromIndex, toIndex);
        }
    }

    /**
     * 주문 상세 정보 조회 (주문 항목 포함, 캐시 적용)
     * 
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 상세 주문 정보
     */
    public Order getOrderWithDetails(Long orderId, Long userId) {
        log.debug("주문 상세 정보 조회 요청: orderId={}, userId={}", orderId, userId);
        
        try {
            String cacheKey = lockKeyGenerator.generateOrderCacheKey(orderId);
            Order cachedOrder = cachePort.get(cacheKey, Order.class, () -> {
                return getOrderUseCase.execute(userId, orderId).orElse(null);
            });
            
            if (cachedOrder != null) {
                log.debug("주문 상세 정보 조회 성공: orderId={}, userId={}", orderId, userId);
                return cachedOrder;
            } else {
                throw new RuntimeException("Order not found");
            }
        } catch (Exception e) {
            log.error("주문 상세 정보 조회 중 오류 발생: orderId={}, userId={}", orderId, userId, e);
            return getOrderUseCase.execute(userId, orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
        }
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
        String paymentLockKey = lockKeyGenerator.generateOrderPaymentKey(orderId);
        String balanceLockKey = lockKeyGenerator.generateBalanceKey(userId);
        
        if (!lockingPort.acquireLock(paymentLockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        
        if (!lockingPort.acquireLock(balanceLockKey)) {
            lockingPort.releaseLock(paymentLockKey);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            return transactionTemplate.execute(status -> {
                if (!userRepositoryPort.existsById(userId)) {
                    throw new UserException.NotFound();
                }
                
                Order order = validateOrderUseCase.execute(orderId, userId);
                BigDecimal finalAmount = applyCouponUseCase.execute(order.getTotalAmount(), couponId);
                deductBalanceUseCase.execute(userId, finalAmount);
                completeOrderUseCase.execute(order);
                
                return createPaymentUseCase.execute(order.getId(), userId, finalAmount);
            });
            
        } finally {
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
    
    /**
     * 사용자 관련 캐시 무효화
     * 
     * @param userId 사용자 ID
     */
    private void invalidateUserRelatedCache(Long userId) {
        try {
            // 사용자의 모든 주문 목록 캐시를 패턴으로 무효화
            String cacheKeyPattern = "order:list:user_" + userId + "_*";
            cachePort.evictByPattern(cacheKeyPattern);
            
            log.debug("사용자 관련 캐시 무효화 완료: userId={}, pattern={}", userId, cacheKeyPattern);
        } catch (Exception e) {
            log.warn("사용자 관련 캐시 무효화 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}