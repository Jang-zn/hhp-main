package kr.hhplus.be.server.api.controller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/balance")
@Validated
public class BalanceController {

    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.OK)
    public void chargeBalance(
            @NotNull(message = "사용자 ID는 필수입니다") @RequestParam Long userId,
            @NotNull(message = "충전 금액은 필수입니다") @DecimalMin(value = "0.0", inclusive = false, message = "충전 금액은 0보다 커야 합니다") @RequestParam BigDecimal amount) {
        // TODO: 잔액 충전 로직 구현 (userId, amount)
    }

    @GetMapping("/{userId}")
    public BalanceResponse getBalance(@PathVariable Long userId) {
        // TODO: 잔액 조회 로직 구현
        // Balance balance = getBalanceUseCase.execute(userId);
        return new BalanceResponse(userId, new java.math.BigDecimal("50000"), java.time.LocalDateTime.now());
    }
} 