package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.CommonException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class CreateOrderFacade {

    private final CreateOrderUseCase createOrderUseCase;
    private final LockingPort lockingPort;

    public CreateOrderFacade(CreateOrderUseCase createOrderUseCase, LockingPort lockingPort) {
        this.createOrderUseCase = createOrderUseCase;
        this.lockingPort = lockingPort;
    }

    @Transactional
    public Order createOrder(Long userId, Map<Long, Integer> productQuantities) {
        String lockKey = "order-creation-" + userId;
        if (!lockingPort.acquireLock(lockKey)) {
            throw new CommonException.ConcurrencyConflict();
        }
        try {
            return createOrderUseCase.execute(userId, productQuantities);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
}