package kr.hhplus.be.server.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔액 변경 이벤트
 * 
 * 잔액 충전/차감이 발생했을 때 발생하는 도메인 이벤트입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceUpdatedEvent {
    
    /**
     * 사용자 ID
     */
    private Long userId;
    
    /**
     * 변경 금액 (양수: 충전, 음수: 차감)
     */
    private BigDecimal amount;
    
    /**
     * 변경 후 잔액
     */
    private BigDecimal currentBalance;
    
    /**
     * 이벤트 발생 시간
     */
    private LocalDateTime updatedAt;
    
    /**
     * 이벤트 타입
     */
    private BalanceEventType eventType;
    
    /**
     * 관련 주문 ID (차감인 경우)
     */
    private Long orderId;
    
    /**
     * 잔액 이벤트 타입 열거형
     */
    public enum BalanceEventType {
        CHARGED,    // 잔액 충전
        DEDUCTED    // 잔액 차감
    }
}