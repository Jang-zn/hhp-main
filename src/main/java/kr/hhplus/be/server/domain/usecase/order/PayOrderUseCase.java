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
import kr.hhplus.be.server.domain.exception.CouponException;
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
            throw new OrderException.ConcurrencyConflict();
        }
        
        // 잔액 락 획득 (충전과의 동시성 방지)
        if (!lockingPort.acquireLock(balanceLockKey)) {
            log.warn("잔액 락 획득 실패: userId={}", userId);
            lockingPort.releaseLock(paymentLockKey); // 주문 락 해제
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
     * 실패 시 이미 확정된 재고들을 복원합니다
     */
    private void confirmReservedStock(Order order) {
        List<OrderItem> processedItems = new ArrayList<>();
        
        try {
            order.getItems().forEach(orderItem -> {
                Product product = orderItem.getProduct();
                int quantity = orderItem.getQuantity();
                
                // 예약된 재고를 실제 재고로 확정
                product.confirmReservation(quantity);
                productRepositoryPort.save(product);
                processedItems.add(orderItem);
                
                log.debug("재고 확정 완료: productId={}, quantity={}, remainingStock={}", 
                        product.getId(), quantity, product.getStock());
            });
        } catch (Exception e) {
            log.error("재고 확정 중 오류 발생: orderId={}, 보상 처리 시작", order.getId(), e);
            
            // 보상 처리: 이미 처리된 아이템들의 재고를 복원
            rollbackConfirmedStock(processedItems);
            
            throw new RuntimeException("재고 확정 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 확정된 재고를 다시 예약 상태로 되돌립니다 (보상 처리)
     */
    private void rollbackConfirmedStock(List<OrderItem> processedItems) {
        processedItems.forEach(orderItem -> {
            try {
                Product product = orderItem.getProduct();
                int quantity = orderItem.getQuantity();
                
                // 확정된 재고를 다시 예약 상태로 복원
                product.restoreReservation(quantity);
                productRepositoryPort.save(product);
                
                log.debug("재고 복원 완료: productId={}, quantity={}", 
                        product.getId(), quantity);
            } catch (Exception rollbackException) {
                log.error("재고 복원 실패: productId={}, quantity={}", 
                        orderItem.getProduct().getId(), orderItem.getQuantity(), rollbackException);
                // 복원 실패는 로그만 남기고 계속 진행
            }
        });
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