package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    
    /**
     * Retrieves a specific order for the given user and order ID.
     *
     * @param userId the ID of the user whose order is to be retrieved
     * @param orderId the ID of the order to retrieve
     * @return an {@code Optional} containing the order if found, or empty if not found or not implemented
     */
    public Optional<Order> execute(Long userId, Long orderId) {
        // TODO: 단일 주문 조회 로직 구현
        return Optional.empty();
    }
} 