package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
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
    
    public void execute(Order order) {
        log.debug("주문 완료 처리: orderId={}", order.getId());
        
        // 예약된 재고를 확정합니다 (실제 재고 차감)
        confirmReservedStock(order);
        
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