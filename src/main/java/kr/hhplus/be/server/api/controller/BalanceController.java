package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.BalanceChargeRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 잔액 관리 Controller
 * 사용자 잔액 충전 및 조회 기능을 제공합니다.
 */
@Tag(name = "잔액 관리", description = "사용자 잔액 충전 및 조회 API")
@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    @ApiSuccess(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다.")
    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.OK)
    public BalanceResponse chargeBalance(@Valid @RequestBody BalanceChargeRequest request) {
        // TODO: 잔액 충전 로직 구현 (request.getUserId(), request.getAmount())
        return new BalanceResponse(request.getUserId(), request.getAmount(), java.time.LocalDateTime.now());
    }

    @ApiSuccess(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다.")
    @GetMapping("/{userId}")
    public BalanceResponse getBalance(@PathVariable Long userId) {
        // TODO: 잔액 조회 로직 구현
        // Balance balance = getBalanceUseCase.execute(userId);
        return new BalanceResponse(userId, new java.math.BigDecimal("50000"), java.time.LocalDateTime.now());
    }
} 