package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
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
    
    public List<Order> execute(Long userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return orderRepositoryPort.findByUser(user);
    }
} 