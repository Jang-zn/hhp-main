package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.usecase.order.CheckOrderAccessUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetOrderFacade {

    private final GetOrderUseCase getOrderUseCase;
    private final CheckOrderAccessUseCase checkOrderAccessUseCase;

    public GetOrderFacade(GetOrderUseCase getOrderUseCase, CheckOrderAccessUseCase checkOrderAccessUseCase) {
        this.getOrderUseCase = getOrderUseCase;
        this.checkOrderAccessUseCase = checkOrderAccessUseCase;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        // 1. 주문 접근 권한 확인
        checkOrderAccessUseCase.execute(userId, orderId);
        
        // 2. 주문 조회
        return getOrderUseCase.execute(userId, orderId).orElse(null);
    }
}