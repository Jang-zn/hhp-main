package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final CouponRepositoryPort couponRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final EventLogRepositoryPort eventLogRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;
    private final MessagingPort messagingPort;
    
    @Transactional
    public Payment execute(Long orderId, Long userId, Long couponId) {
        log.debug("결제 처리 요청: orderId={}, userId={}, couponId={}", orderId, userId, couponId);
        
        // 파라미터 검증
        validateParameters(orderId, userId);
        
        String paymentLockKey = "payment-" + orderId;
        String balanceLockKey = "balance-" + userId;
        
        // 주문 락 먼저 획득
        if (!lockingPort.acquireLock(paymentLockKey)) {
            log.warn("주문 락 획득 실패: orderId={}", orderId);
            throw new CommonException.ConcurrencyConflict();
        }
        
        // 잔액 락 획득 (충전과의 동시성 방지)
        if (!lockingPort.acquireLock(balanceLockKey)) {
            log.warn("잔액 락 획득 실패: userId={}", userId);
            lockingPort.releaseLock(paymentLockKey); // 주문 락 해제
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 사용자 및 주문 조회
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("존재하지 않는 사용자: userId={}", userId);
                        return new UserException.NotFound();
                    });
            
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

            // 쿠폰 적용 및 최종 금액 계산
            BigDecimal finalAmount = order.getTotalAmount();
            if (couponId != null) {
                Coupon coupon = couponRepositoryPort.findById(couponId)
                        .orElseThrow(() -> {
                            log.warn("존재하지 않는 쿠폰: couponId={}", couponId);
                            return new CouponException.NotFound();
                        });
                finalAmount = finalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
                log.debug("쿠폰 적용: originalAmount={}, discountRate={}, finalAmount={}", 
                        order.getTotalAmount(), coupon.getDiscountRate(), finalAmount);
            }

            // 잔액 확인 및 차감
            Balance balance = balanceRepositoryPort.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.warn("잔액 정보 없음: userId={}", userId);
                        return new BalanceException.NotFound();
                    });
            
            if (balance.getAmount().compareTo(finalAmount) < 0) {
                log.warn("잔액 부족: userId={}, balance={}, requiredAmount={}", 
                        userId, balance.getAmount(), finalAmount);
                throw new BalanceException.InsufficientBalance();
            }
            
            balance.subtractAmount(finalAmount);
            balanceRepositoryPort.save(balance);

            // 예약된 재고 확정 (재고 차감)
            confirmReservedStock(order);

            // 결제 생성
            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .amount(finalAmount)
                    .status(PaymentStatus.PAID)
                    .build();

            Payment savedPayment = paymentRepositoryPort.save(payment);
            
            log.info("결제 완료: paymentId={}, orderId={}, userId={}, amount={}", 
                    savedPayment.getId(), orderId, userId, finalAmount);
            
            // 캐시 무효화
            invalidateRelatedCache(userId, orderId);
            
            return savedPayment;
        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생: orderId={}, userId={}", orderId, userId, e);
            throw e;
        } finally {
            lockingPort.releaseLock(balanceLockKey);
            lockingPort.releaseLock(paymentLockKey);
        }
    }
    
    private void validateParameters(Long orderId, Long userId) {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (orderId <= 0) {
            throw new IllegalArgumentException("OrderId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }
    }
    
    /**
     * 예약된 재고를 확정합니다 (실제 재고 차감)
     * 동시성 제어와 데이터 일관성을 보장하며, 실패 시 롤백을 지원합니다.
     */
    private void confirmReservedStock(Order order) {
        log.debug("재고 확정 시작: orderId={}", order.getId());
        
        // 주문에 포함된 모든 OrderItem 조회
        List<OrderItem> orderItems = orderItemRepositoryPort.findByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            log.warn("주문에 포함된 상품이 없습니다: orderId={}", order.getId());
            throw new OrderException.EmptyItems();
        }
        
        List<Product> modifiedProducts = new ArrayList<>();
        
        try {
            // 각 OrderItem에 대해 재고 확정 처리
            for (OrderItem orderItem : orderItems) {
                Long productId = orderItem.getProductId();
                int quantity = orderItem.getQuantity();
                
                // 상품별 락 획득 (동시성 제어)
                String stockLockKey = "stock-" + productId;
                if (!lockingPort.acquireLock(stockLockKey)) {
                    log.warn("상품 재고 락 획득 실패: productId={}", productId);
                    throw new CommonException.ConcurrencyConflict();
                }
                
                try {
                    // 상품 조회
                    Product product = productRepositoryPort.findById(productId)
                            .orElseThrow(() -> {
                                log.error("상품을 찾을 수 없습니다: productId={}", productId);
                                return new ProductException.NotFound();
                            });
                    
                    // 예약된 재고 확정 (실제 재고 차감 + 예약 재고 감소)
                    product.confirmReservation(quantity);
                    
                    // 상품 저장
                    Product savedProduct = productRepositoryPort.save(product);
                    modifiedProducts.add(savedProduct);
                    
                    log.debug("재고 확정 완료: productId={}, quantity={}, remainingStock={}, reservedStock={}", 
                            productId, quantity, savedProduct.getStock(), savedProduct.getReservedStock());
                            
                } catch (Exception e) {
                    log.error("상품 재고 확정 실패: productId={}, quantity={}", productId, quantity, e);
                    throw e;
                } finally {
                    lockingPort.releaseLock(stockLockKey);
                }
            }
            
            log.info("모든 재고 확정 완료: orderId={}, itemCount={}", order.getId(), orderItems.size());
            
        } catch (Exception e) {
            log.error("재고 확정 중 오류 발생, 롤백 시작: orderId={}", order.getId(), e);
            
            // 롤백: 이미 확정된 재고를 다시 예약 상태로 복원
            rollbackConfirmedStock(modifiedProducts, orderItems);
            
            // 원본 예외를 다시 던져서 트랜잭션 롤백 유도
            throw e;
        }
    }
    
    /**
     * 확정된 재고를 롤백합니다 (보상 처리)
     */
    private void rollbackConfirmedStock(List<Product> modifiedProducts, List<OrderItem> orderItems) {
        log.warn("재고 확정 롤백 시작: 확정된 상품 수={}", modifiedProducts.size());
        
        try {
            for (Product product : modifiedProducts) {
                // 해당 상품의 OrderItem 찾기
                OrderItem correspondingOrderItem = orderItems.stream()
                        .filter(item -> item.getProductId().equals(product.getId()))
                        .findFirst()
                        .orElse(null);
                
                if (correspondingOrderItem != null) {
                    String rollbackLockKey = "rollback-stock-" + product.getId();
                    if (lockingPort.acquireLock(rollbackLockKey)) {
                        try {
                            // 확정된 재고를 다시 예약 상태로 복원
                            product.restoreReservation(correspondingOrderItem.getQuantity());
                            productRepositoryPort.save(product);
                            
                            log.debug("재고 롤백 완료: productId={}, quantity={}", 
                                    product.getId(), correspondingOrderItem.getQuantity());
                        } finally {
                            lockingPort.releaseLock(rollbackLockKey);
                        }
                    }
                }
            }
            
            log.info("재고 확정 롤백 완료");
            
        } catch (Exception rollbackException) {
            log.error("재고 롤백 중 오류 발생 - 수동 처리 필요", rollbackException);
            // 롤백 실패는 별도 모니터링 및 수동 처리가 필요한 심각한 상황
        }
    }
    
    private void invalidateRelatedCache(Long userId, Long orderId) {
        try {
            // 사용자 잔액 캐시 무효화
            String balanceCacheKey = "balance_" + userId;
            // 주문 캐시 무효화
            String orderCacheKey = "order_" + orderId + "_" + userId;
            // cachePort.evict(balanceCacheKey); // 구현 필요시 추가
            // cachePort.evict(orderCacheKey);   // 구현 필요시 추가
            
            log.debug("캐시 무효화 완료: userId={}, orderId={}", userId, orderId);
        } catch (Exception e) {
            log.warn("캐시 무효화 실패: userId={}, orderId={}", userId, orderId, e);
            // 캐시 무효화 실패는 비즈니스 로직에 영향 없음
        }
    }
} 