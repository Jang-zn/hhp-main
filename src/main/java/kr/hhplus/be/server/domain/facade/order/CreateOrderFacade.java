package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class CreateOrderFacade {

    private final CreateOrderUseCase createOrderUseCase;

    public CreateOrderFacade(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @Transactional
    public Order createOrder(Long userId, Map<Long, Integer> productQuantities) {
        return createOrderUseCase.execute(userId, productQuantities);
    }
}