package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderUseCase {
    
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    public Optional<Order> execute(Long userId, Long orderId) {
        log.debug("주문 조회 요청: userId={}, orderId={}", userId, orderId);
        
        // 파라미터 검증
        validateParameters(userId, orderId);
        
        // 사용자 존재 확인
        if (!userRepositoryPort.existsById(userId)) {
            log.warn("존재하지 않는 사용자: userId={}", userId);
            throw new UserException.NotFound();
        }
        
        try {
            String cacheKey = keyGenerator.generateOrderCacheKey(orderId);
            
            // 캐시에서 조회 시도
            Order cachedOrder = cachePort.get(cacheKey, Order.class);
            
            if (cachedOrder != null) {
                // 주문이 해당 사용자의 것인지 확인
                if (!cachedOrder.getUserId().equals(userId)) {
                    log.warn("주문 접근 권한 없음: orderId={}, requestUserId={}, orderUserId={}", 
                        orderId, userId, cachedOrder.getUserId());
                    throw new OrderException.Unauthorized(); // 403 Forbidden
                }
                log.debug("캐시에서 주문 조회 성공: userId={}, orderId={}", userId, orderId);
                return Optional.of(cachedOrder);
            }
            
            // 캐시 미스 - 데이터베이스에서 조회
            Optional<Order> orderOpt = orderRepositoryPort.findById(orderId);
            
            if (orderOpt.isEmpty()) {
                log.debug("주문이 존재하지 않음: orderId={}", orderId);
                return Optional.empty(); // OrderService에서 OrderException.NotFound(404)로 변환됨
            }
            
            Order order = orderOpt.get();
            
            // 주문이 해당 사용자의 것인지 확인
            if (!order.getUserId().equals(userId)) {
                log.warn("주문 접근 권한 없음: orderId={}, requestUserId={}, orderUserId={}", 
                    orderId, userId, order.getUserId());
                throw new OrderException.Unauthorized(); // 403 Forbidden
            }
            
            // 캐시에 저장
            cachePort.put(cacheKey, order, CacheTTL.ORDER_DETAIL.getSeconds());
            
            log.debug("주문 조회 성공: userId={}, orderId={}", userId, orderId);
            return Optional.of(order);
            
        } catch (OrderException | UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("주문 조회 중 오류 발생: userId={}, orderId={}", userId, orderId, e);
            // 캐시 오류 시 직접 DB에서 조회
            Optional<Order> orderOpt = orderRepositoryPort.findById(orderId);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                if (!order.getUserId().equals(userId)) {
                    throw new OrderException.Unauthorized();
                }
                return Optional.of(order);
            }
            return Optional.empty();
        }
    }
    
    private void validateParameters(Long userId, Long orderId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
    }
} 