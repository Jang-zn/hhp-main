package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompleteOrderUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    
    public void execute(Order order) {
        log.debug("주문 완료 처리: orderId={}", order.getId());
        
        // 예약된 재고를 확정합니다 (실제 재고 차감)
        confirmReservedStock(order);
        
        log.info("주문 완료 처리 완료: orderId={}", order.getId());
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
            
            // InvalidReservation 예외 처리 - 실제 재고 부족인 경우에만 OutOfStock으로 변환
            if (e instanceof ProductException.InvalidReservation) {
                ProductException.InvalidReservation invalidReservationException = (ProductException.InvalidReservation) e;
                // 실제 재고 부족 메시지인 경우에만 OutOfStock으로 변환
                if (invalidReservationException.getMessage().contains("insufficient actual stock")) {
                    throw new ProductException.OutOfStock();
                }
                // 예약 수량 초과인 경우 원래 예외를 다시 던짐
                throw invalidReservationException;
            }
            
            // cause가 InvalidReservation인 경우도 처리
            if (e.getCause() instanceof ProductException.InvalidReservation) {
                ProductException.InvalidReservation invalidReservationException = (ProductException.InvalidReservation) e.getCause();
                // 실제 재고 부족 메시지인 경우에만 OutOfStock으로 변환
                if (invalidReservationException.getMessage().contains("insufficient actual stock")) {
                    throw new ProductException.OutOfStock();
                }
                // 예약 수량 초과인 경우 원래 예외를 다시 던짐
                throw invalidReservationException;
            }
            
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
}