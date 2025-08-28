package kr.hhplus.be.server.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급 이벤트
 * 
 * 쿠폰이 발급되거나 사용되었을 때 발생하는 도메인 이벤트입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponIssuedEvent {
    
    /**
     * 쿠폰 ID
     */
    private Long couponId;
    
    /**
     * 사용자 ID
     */
    private Long userId;
    
    /**
     * 쿠폰 이름
     */
    private String couponName;
    
    /**
     * 할인 금액
     */
    private BigDecimal discountAmount;
    
    /**
     * 이벤트 발생 시간
     */
    private LocalDateTime issuedAt;
    
    /**
     * 이벤트 타입 (ISSUED, USED, EXPIRED 등)
     */
    private CouponEventType eventType;
    
    /**
     * 쿠폰 이벤트 타입 열거형
     */
    public enum CouponEventType {
        ISSUED,     // 쿠폰 발급
        USED,       // 쿠폰 사용
        EXPIRED,    // 쿠폰 만료
        STOCK_UPDATED // 쿠폰 재고 업데이트
    }
}