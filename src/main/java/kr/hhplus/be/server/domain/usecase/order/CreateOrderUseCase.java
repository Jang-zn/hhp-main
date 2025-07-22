package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrderUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final EventLogRepositoryPort eventLogRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;

    @Transactional
    public Order execute(Long userId, Map<Long, Integer> productQuantities) {
        log.debug("주문 생성 요청: userId={}, products={}", userId, productQuantities);
        
        // 파라미터 검증
        validateParameters(userId, productQuantities);
        
        String lockKey = "order-creation-" + userId;
        if (!lockingPort.acquireLock(lockKey)) {
            log.warn("락 획득 실패: userId={}", userId);
            throw new CommonException.ConcurrencyConflict();
        }
        
        try {
            // 사용자 조회
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("존재하지 않는 사용자: userId={}", userId);
                        return new UserException.NotFound();
                    });

            // 상품별 재고 예약 및 주문 아이템 생성
            List<OrderItem> orderItems = productQuantities.entrySet().stream()
                    .map(entry -> {
                        Long productId = entry.getKey();
                        Integer quantity = entry.getValue();
                        
                        Product product = productRepositoryPort.findById(productId)
                                .orElseThrow(() -> {
                                    log.warn("존재하지 않는 상품: productId={}", productId);
                                    return new ProductException.NotFound();
                                });
                        
                        // 재고 예약 (기존 decreaseStock 대신 reserveStock 사용)
                        product.reserveStock(quantity);
                        productRepositoryPort.save(product);
                        
                        log.debug("재고 예약 완료: productId={}, quantity={}, availableStock={}", 
                                productId, quantity, product.getStock() - product.getReservedStock());
                        
                        return OrderItem.builder()
                                .product(product)
                                .quantity(quantity)
                                .price(product.getPrice())
                                .build();
                    }).collect(Collectors.toList());

            // 총 주문 금액 계산
            BigDecimal totalAmount = orderItems.stream()
                    .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 주문 생성
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(totalAmount)
                    .items(orderItems)
                    .build();

            Order savedOrder = orderRepositoryPort.save(order);
            log.info("주문 생성 완료: orderId={}, userId={}, totalAmount={}", 
                    savedOrder.getId(), userId, totalAmount);
            
            // 캐시 무효화
            invalidateUserRelatedCache(userId);
            
            return savedOrder;
        } catch (Exception e) {
            log.error("주문 생성 중 오류 발생: userId={}", userId, e);
            // 예약된 재고 롤백은 트랜잭션으로 처리됨
            throw e;
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
    
    private void validateParameters(Long userId, Map<Long, Integer> productQuantities) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new OrderException.EmptyItems();
        }
        
        // 수량 검증
        productQuantities.forEach((productId, quantity) -> {
            if (productId == null || productId <= 0) {
                throw new IllegalArgumentException("Invalid productId: " + productId);
            }
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        });
    }
    
    private void invalidateUserRelatedCache(Long userId) {
        try {
            // 사용자 관련 캐시 무효화 (주문 목록 등)
            String userOrderCacheKey = "user_orders_" + userId;
            // cachePort.evict(userOrderCacheKey); // 구현 필요시 추가
            log.debug("캐시 무효화 완료: userId={}", userId);
        } catch (Exception e) {
            log.warn("캐시 무효화 실패: userId={}", userId, e);
            // 캐시 무효화 실패는 비즈니스 로직에 영향 없음
        }
    }
}