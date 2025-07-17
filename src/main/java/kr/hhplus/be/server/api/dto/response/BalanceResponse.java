package kr.hhplus.be.server.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceResponse(
        Long userId,
        BigDecimal amount,
        LocalDateTime updatedAt
) {} 