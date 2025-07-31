package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GetOrderListFacade {

    private final GetOrderListUseCase getOrderListUseCase;

    public GetOrderListFacade(GetOrderListUseCase getOrderListUseCase) {
        this.getOrderListUseCase = getOrderListUseCase;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrderList(Long userId, int limit, int offset) {
        return getOrderListUseCase.execute(userId);
    }
}