package kr.hhplus.be.server.domain.dto;

import kr.hhplus.be.server.domain.entity.Order;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 정보를 담는 DTO
 * OrderItem과 Product 정보가 매핑된 완전한 주문 정보를 제공
 */
@Getter
@AllArgsConstructor
public class OrderWithDetailsDto {
    private final Long orderId;
    private final Long userId;
    private final String status;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;
    private final List<OrderItemDetailDto> items;

    /**
     * Order 엔티티로부터 기본 정보를 생성하는 생성자
     */
    public OrderWithDetailsDto(Order order, List<OrderItemDetailDto> items) {
        this.orderId = order.getId();
        this.userId = order.getUserId();
        this.status = order.getStatus().name();
        this.totalAmount = order.getTotalAmount();
        this.createdAt = order.getCreatedAt();
        this.items = items;
    }

    /**
     * 주문 아이템 상세 정보를 담는 DTO
     */
    @Getter
    @AllArgsConstructor
    public static class OrderItemDetailDto {
        private final Long productId;
        private final String productName;
        private final int quantity;
        private final BigDecimal price;
    }
}