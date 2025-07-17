package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.ApiMessage;
import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    @PostMapping("/charge")
    public ResponseEntity<CommonResponse<Object>> chargeBalance(
            @RequestParam Long userId,
            @RequestParam BigDecimal amount) {
        // TODO: 잔액 충전 로직 구현
        return CommonResponse.ok(ApiMessage.BALANCE_CHARGED.getMessage());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<CommonResponse<Object>> getBalance(@PathVariable Long userId) {
        // TODO: 잔액 조회 로직 구현
        // Balance balance = getBalanceUseCase.execute(userId);
        return CommonResponse.ok(ApiMessage.BALANCE_RETRIEVED.getMessage(), null); // 나중에 실제 balance 데이터로 교체
    }
} 