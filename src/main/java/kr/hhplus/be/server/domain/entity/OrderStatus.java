package kr.hhplus.be.server.domain.entity;

/**
 * 주문 상태를 나타내는 열거형
 */
public enum OrderStatus {
    /**
     * 주문 생성됨 (결제 대기 중)
     */
    PENDING,
    
    /**
     * 결제 완료됨
     */
    PAID,
    
    /**
     * 주문 취소됨
     */
    CANCELLED,
    
    /**
     * 주문 완료됨
     */
    COMPLETED
}