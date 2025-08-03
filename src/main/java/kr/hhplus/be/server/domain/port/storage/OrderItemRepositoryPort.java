package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.OrderItem;

import java.util.List;

/**
 * OrderItem 저장소 포트
 */
public interface OrderItemRepositoryPort {
    /**
     * OrderItem을 저장합니다.
     */
    OrderItem save(OrderItem orderItem);
    
    /**
     * 여러 OrderItem을 저장합니다.
     */
    List<OrderItem> saveAll(List<OrderItem> orderItems);
    
    /**
     * 주문 ID로 OrderItem 목록을 조회합니다.
     */
    List<OrderItem> findByOrderId(Long orderId);
}