package kr.hhplus.be.server.domain.enums;

public enum EventType {
    // 기존 내부 이벤트
    ORDER_CREATED,
    ORDER_COMPLETED,
    PAYMENT_COMPLETED,
    BALANCE_CHANGED,
    
    ORDER_DATA_SYNC,           // 주문 데이터 플랫폼 동기화
    PAYMENT_DATA_SYNC,         // 결제 데이터 플랫폼 동기화
    BALANCE_TRANSACTION_SYNC,  // 잔액 거래 동기화
    PRODUCT_STOCK_SYNC,        // 재고 변동 동기화
    USER_ACTIVITY_SYNC
} 