package kr.hhplus.be.server.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 결제 완료 이벤트
 */
@Getter
@AllArgsConstructor
public class PaymentCompletedEvent {
    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final BigDecimal amount;
}