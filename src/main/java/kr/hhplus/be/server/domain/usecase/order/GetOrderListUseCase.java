package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
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
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;

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
        
        try {
            String cacheKey = keyGenerator.generateOrderListCacheKey(userId, limit, offset);
            
            // 캐시에서 조회 시도
            @SuppressWarnings("unchecked")
            List<Order> cachedOrders = cachePort.get(cacheKey, List.class);
            
            if (cachedOrders != null) {
                log.debug("캐시에서 주문 목록 조회 성공: userId={}, returned={}", userId, cachedOrders.size());
                return cachedOrders;
            }
            
            // 캐시 미스 - 데이터베이스에서 주문 목록 조회 (페이징 지원)
            PageRequest pageable = PageRequest.of(offset / limit, limit);
            List<Order> paginatedOrders = orderRepositoryPort.findByUserId(userId, pageable);
            
            if (!paginatedOrders.isEmpty()) {
                log.debug("데이터베이스에서 주문 목록 조회: userId={}, returned={}", userId, paginatedOrders.size());
                
                // 캐시에 저장
                cachePort.put(cacheKey, paginatedOrders, CacheTTL.ORDER_LIST.getSeconds());
            } else {
                log.debug("주문 목록 조회 결과 없음: userId={}", userId);
            }
            
            return paginatedOrders;
            
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("주문 목록 조회 중 오류 발생: userId={}, limit={}, offset={}", userId, limit, offset, e);
            // 캐시 오류 시 직접 DB에서 조회
            PageRequest pageable = PageRequest.of(offset / limit, limit);
            return orderRepositoryPort.findByUserId(userId, pageable);
        }
    }
}