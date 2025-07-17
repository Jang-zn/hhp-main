package kr.hhplus.be.server.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String status,
        BigDecimal finalAmount,
        LocalDateTime paidAt
) {} 