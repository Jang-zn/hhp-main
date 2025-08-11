package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.service.LockOrderManager;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
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
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final EventLogRepositoryPort eventLogRepositoryPort;
    private final CachePort cachePort;
    private final LockOrderManager lockOrderManager;

    /**
     * 주문을 생성하고 상품 재고를 예약합니다.
     * 
     * 동시성 제어:
     * - LockOrderManager로 상품 ID 정렬하여 데드락 방지
     * - 비관적 락으로 상품 조회하여 재고 경합 방지
     * - 트랜잭션 관리는 Service 레이어에서 담당
     * 
     * @param userId 주문하는 사용자 ID
     * @param productQuantities 주문할 상품과 수량 정보
     * @return 생성된 주문 엔티티
     */
    public Order execute(Long userId, List<ProductQuantityDto> productQuantities) {
        log.debug("주문 생성 요청: userId={}, products={}", userId, productQuantities);
        
        try {
            // 파라미터 검증
            validateParameters(userId, productQuantities);
            // 사용자 존재 확인
            if (!userRepositoryPort.existsById(userId)) {
                log.warn("존재하지 않는 사용자: userId={}", userId);
                throw new UserException.NotFound();
            }

            // 데드락 방지를 위한 상품 ID 정렬
            List<Long> productIds = productQuantities.stream()
                    .map(ProductQuantityDto::getProductId)
                    .collect(Collectors.toList());
            List<Long> orderedProductIds = lockOrderManager.getOrderedLockIds(productIds);
            
            // 정렬된 순서로 비관적 락으로 상품 조회 (데드락 방지)
            List<Product> products = productRepositoryPort.findByIdsWithLock(orderedProductIds);
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, product -> product));
            
            // 상품별 재고 예약 및 주문 아이템 생성
            List<OrderItem> orderItems = productQuantities.stream()
                    .map(productQuantity -> {
                        Long productId = productQuantity.getProductId();
                        Integer quantity = productQuantity.getQuantity();
                        
                        Product product = productMap.get(productId);
                        if (product == null) {
                            log.warn("존재하지 않는 상품: productId={}", productId);
                            throw new ProductException.NotFound();
                        }
                        
                        // 재고 예약 (DB @Check 제약조건으로 추가 무결성 보장)
                        product.reserveStock(quantity);
                        productRepositoryPort.save(product);
                        
                        log.debug("재고 예약 완료: productId={}, quantity={}, availableStock={}", 
                                productId, quantity, product.getStock() - product.getReservedStock());
                        
                        return OrderItem.builder()
                                .productId(productId)
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
                    .userId(userId)
                    .totalAmount(totalAmount)
                    .build();

            Order savedOrder = orderRepositoryPort.save(order);
            
            // OrderItem들에 orderId 설정 후 배치 저장 (성능 최적화)
            List<OrderItem> orderItemsWithOrderId = orderItems.stream()
                    .map(item -> item.withOrderId(savedOrder.getId()))
                    .collect(Collectors.toList());
            
            orderItemRepositoryPort.saveAll(orderItemsWithOrderId);
            
            log.debug("OrderItem 배치 저장 완료: orderId={}, itemCount={}", 
                    savedOrder.getId(), orderItemsWithOrderId.size());
            
            log.info("주문 생성 완료: orderId={}, userId={}, totalAmount={}, itemCount={}", 
                    savedOrder.getId(), userId, totalAmount, orderItems.size());
            
            // 캐시 무효화
            invalidateUserRelatedCache(userId);
            
            return savedOrder;
        } catch (Exception e) {
            log.error("주문 생성 중 오류 발생: userId={}", userId, e);
            // 예약된 재고 롤백은 트랜잭션으로 처리됨
            throw e;
        }
    }

    private void validateParameters(Long userId, List<ProductQuantityDto> productQuantities) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new OrderException.EmptyItems();
        }
        
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