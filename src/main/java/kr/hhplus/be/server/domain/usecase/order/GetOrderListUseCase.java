package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderListUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;

    public List<Order> execute(Long userId) {
        return execute(userId, 50, 0); // 기본값으로 호출
    }
    
    public List<Order> execute(Long userId, int limit, int offset) {
        log.debug("주문 목록 조회 요청: userId={}, limit={}, offset={}", userId, limit, offset);
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("존재하지 않는 사용자: userId={}", userId);
            throw new UserException.NotFound();
        }
        
        // 데이터베이스에서 주문 목록 조회 (페이징 지원)
        PageRequest pageable = PageRequest.of(offset / limit, limit);
        List<Order> paginatedOrders = orderRepositoryPort.findByUserId(userId, pageable);
        
        if (!paginatedOrders.isEmpty()) {
            log.debug("주문 목록 조회 성공: userId={}, returned={}", userId, paginatedOrders.size());
        } else {
            log.debug("주문 목록 조회 결과 없음: userId={}", userId);
        }
        
        return paginatedOrders;
    }
}