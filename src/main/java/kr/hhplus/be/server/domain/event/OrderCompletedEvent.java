package kr.hhplus.be.server.domain.event;

import java.time.LocalDateTime;
import java.util.List;

public class OrderCompletedEvent {
    private final Long orderId;
    private final Long userId;
    private final List<ProductOrderInfo> productOrders;
    private final LocalDateTime completedAt;
    
    public OrderCompletedEvent(Long orderId, Long userId, List<ProductOrderInfo> productOrders) {
        this.orderId = orderId;
        this.userId = userId;
        this.productOrders = productOrders;
        this.completedAt = LocalDateTime.now();
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public List<ProductOrderInfo> getProductOrders() {
        return productOrders;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public static class ProductOrderInfo {
        private final Long productId;
        private final int quantity;
        
        public ProductOrderInfo(Long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
        
        public Long getProductId() {
            return productId;
        }
        
        public int getQuantity() {
            return quantity;
        }
    }
}