package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetOrderListUseCase {
    
    private final StoragePort storagePort;
    
    public List<Order> execute(Long userId) {
        // TODO: 주문 목록 조회 로직 구현
        return List.of();
    }
} 