package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetOrderListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    
    /**
     * Retrieves a list of orders associated with the specified user ID.
     *
     * @param userId the ID of the user whose orders are to be retrieved
     * @return a list of orders for the given user; currently returns an empty list as the logic is not yet implemented
     */
    public List<Order> execute(Long userId) {
        // TODO: 주문 목록 조회 로직 구현
        return List.of();
    }
} 