package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompleteOrderUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    public void execute(Order order) {
        log.debug("주문 완료 처리: orderId={}", order.getId());
        
        // 예약된 재고를 확정합니다 (실제 재고 차감)
        confirmReservedStock(order);
        
        // 주문 완료 후 캐시 무효화
        try {
            // 주문 상세 캐시 무효화 (미래에 상태 변경 시 업데이트 대비)
            String orderCacheKey = keyGenerator.generateOrderCacheKey(order.getId());
            cachePort.evict(orderCacheKey);
            log.debug("주문 캐시 무효화 완료: orderId={}", order.getId());
            
            // 주문 목록 캐시 무효화
            String pattern = keyGenerator.generateOrderListCachePattern(order.getUserId());
            cachePort.evictByPattern(pattern);
            log.debug("주문 목록 캐시 무효화 완료: userId={}", order.getUserId());
        } catch (Exception e) {
            log.warn("주문 완료 캐시 처리 실패: orderId={}, userId={}", order.getId(), order.getUserId(), e);
            // 캐시 오류는 비즈니스 로직에 영향을 주지 않음
        }
        
        log.info("주문 완료 처리 완료: orderId={}", order.getId());
    }
    
    /**
     * 예약된 재고를 확정합니다 (실제 재고 차감)
     */
    private void confirmReservedStock(Order order) {
        log.debug("재고 확정 처리 시작: orderId={}", order.getId());
        
        // OrderItem 조회
        List<OrderItem> orderItems = orderItemRepositoryPort.findByOrderId(order.getId());
        
        if (orderItems.isEmpty()) {
            log.warn("주문에 OrderItem이 없습니다: orderId={}", order.getId());
            return;
        }
        
        // 각 OrderItem에 대해 재고 확정 처리
        for (OrderItem orderItem : orderItems) {
            Product product = productRepositoryPort.findById(orderItem.getProductId())
                    .orElseThrow(() -> {
                        log.error("상품을 찾을 수 없습니다: productId={}", orderItem.getProductId());
                        return new ProductException.NotFound();
                    });
            
            // 예약된 재고를 실제 재고에서 차감
            try {
                product.confirmReservation(orderItem.getQuantity());
                productRepositoryPort.save(product);
                
                log.debug("재고 확정 완료: productId={}, quantity={}, remainingStock={}", 
                        orderItem.getProductId(), orderItem.getQuantity(), product.getStock());
            } catch (Exception e) {
                log.error("재고 확정 실패: productId={}, quantity={}", 
                        orderItem.getProductId(), orderItem.getQuantity(), e);
                throw e;
            }
        }
        
        log.debug("재고 확정 처리 완료: orderId={}, itemCount={}", order.getId(), orderItems.size());
    }
}