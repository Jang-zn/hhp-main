package kr.hhplus.be.server.domain.facade.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class GetBalanceFacade {

    private final GetBalanceUseCase getBalanceUseCase;

    public GetBalanceFacade(GetBalanceUseCase getBalanceUseCase) {
        this.getBalanceUseCase = getBalanceUseCase;
    }

    @Transactional(readOnly = true)
    public Optional<Balance> getBalance(Long userId) {
        return getBalanceUseCase.execute(userId);
    }
}