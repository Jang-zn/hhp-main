package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderItem 저장소 포트
 */
@Repository
public interface OrderItemRepositoryPort extends JpaRepository<OrderItem, Long> {
    /**
     * 주문 ID로 OrderItem 목록을 조회합니다.
     */
    List<OrderItem> findByOrderId(Long orderId);
}