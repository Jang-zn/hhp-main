package kr.hhplus.be.server.domain.enums;

/**
 * 이벤트 토픽 관리 Enum
 */
public enum EventTopic {
    
    // 내부 도메인 이벤트
    ORDER_COMPLETED("order.completed"),
    PRODUCT_CREATED("product.created"),
    PRODUCT_UPDATED("product.updated"),
    PRODUCT_DELETED("product.deleted"),
    
    // 외부 데이터 플랫폼 동기화 이벤트
    DATA_PLATFORM_PRODUCT_CREATED("data-platform.product.created"),
    DATA_PLATFORM_PRODUCT_UPDATED("data-platform.product.updated"),
    DATA_PLATFORM_PRODUCT_DELETED("data-platform.product.deleted"),
    DATA_PLATFORM_PAYMENT_COMPLETED("data-platform.payment.completed");
    
    private final String topic;
    
    EventTopic(String topic) {
        this.topic = topic;
    }
    
    public String getTopic() {
        return topic;
    }
    
    @Override
    public String toString() {
        return topic;
    }
}