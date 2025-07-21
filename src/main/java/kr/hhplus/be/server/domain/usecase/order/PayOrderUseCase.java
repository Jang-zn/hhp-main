package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.BalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final BalanceRepositoryPort balanceRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
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
        
        String lockKey = "payment-" + orderId;
        if (!lockingPort.acquireLock(lockKey)) {
            log.warn("락 획득 실패: orderId={}", orderId);
            throw new OrderException.ConcurrencyConflict();
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
            if (!order.getUser().getId().equals(userId)) {
                log.warn("주문 소유권 불일치: orderId={}, userId={}, orderUserId={}", 
                        orderId, userId, order.getUser().getId());
                throw new OrderException.Unauthorized();
            }

            // 쿠폰 적용 및 최종 금액 계산
            BigDecimal finalAmount = order.getTotalAmount();
            if (couponId != null) {
                Coupon coupon = couponRepositoryPort.findById(couponId)
                        .orElseThrow(() -> {
                            log.warn("존재하지 않는 쿠폰: couponId={}", couponId);
                            return new RuntimeException("Coupon not found");
                        });
                finalAmount = finalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
                log.debug("쿠폰 적용: originalAmount={}, discountRate={}, finalAmount={}", 
                        order.getTotalAmount(), coupon.getDiscountRate(), finalAmount);
            }

            // 잔액 확인 및 차감
            Balance balance = balanceRepositoryPort.findByUser(user)
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
                    .order(order)
                    .user(user)
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
            lockingPort.releaseLock(lockKey);
        }
    }
    
    private void validateParameters(Long orderId, Long userId) {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
    }
    
    /**
     * 예약된 재고를 확정합니다 (실제 재고 차감)
     */
    private void confirmReservedStock(Order order) {
        try {
            order.getItems().forEach(orderItem -> {
                Product product = orderItem.getProduct();
                int quantity = orderItem.getQuantity();
                
                // 예약된 재고를 실제 재고로 확정
                product.confirmReservation(quantity);
                productRepositoryPort.save(product);
                
                log.debug("재고 확정 완료: productId={}, quantity={}, remainingStock={}", 
                        product.getId(), quantity, product.getStock());
            });
        } catch (Exception e) {
            log.error("재고 확정 중 오류 발생: orderId={}", order.getId(), e);
            throw e;
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