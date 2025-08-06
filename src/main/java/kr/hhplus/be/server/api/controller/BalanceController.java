package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import kr.hhplus.be.server.api.docs.annotation.BalanceApiDocs;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.facade.balance.ChargeBalanceFacade;
import kr.hhplus.be.server.domain.facade.balance.GetBalanceFacade;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 잔액 관리 Controller
 * 사용자 잔액 충전 및 조회 기능을 제공합니다.
 */
@Tag(name = "잔액 관리", description = "사용자 잔액 충전 및 조회 API")
@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
@Validated
public class BalanceController {

    private final ChargeBalanceFacade chargeBalanceFacade;
    private final GetBalanceFacade getBalanceFacade;

    @BalanceApiDocs(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다")
    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.OK)
    public BalanceResponse chargeBalance(@Valid @RequestBody BalanceRequest request) {
        
        Balance balance = chargeBalanceFacade.chargeBalance(request.getUserId(), request.getAmount());
        return new BalanceResponse(
                balance.getUserId(),
                balance.getAmount(),
                balance.getUpdatedAt()
        );
    }

    @BalanceApiDocs(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다")
    @GetMapping("/{userId}")
    public BalanceResponse getBalance(@PathVariable @Positive Long userId) {
        
        Optional<Balance> balanceOpt = getBalanceFacade.getBalance(userId);
        
        if (balanceOpt.isEmpty()) {
            throw new UserException.InvalidUser();
        }
        
        Balance balance = balanceOpt.get();
        return new BalanceResponse(
                balance.getUserId(),
                balance.getAmount(),
                balance.getUpdatedAt()
        );
    }
} 