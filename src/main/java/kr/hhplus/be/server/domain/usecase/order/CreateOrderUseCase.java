package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final EventLogRepositoryPort eventLogRepositoryPort;
    private final LockingPort lockingPort;
    private final CachePort cachePort;

    public Order execute(Long userId, Map<Long, Integer> productQuantities) {
        String lockKey = "order-creation-" + userId;
        if (!lockingPort.acquireLock(lockKey)) {
            throw new RuntimeException("Failed to acquire lock");
        }
        try {
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<OrderItem> orderItems = productQuantities.entrySet().stream()
                    .map(entry -> {
                        Product product = productRepositoryPort.findById(entry.getKey())
                                .orElseThrow(() -> new RuntimeException("Product not found"));
                        product.decreaseStock(entry.getValue());
                        productRepositoryPort.save(product);
                        return OrderItem.builder()
                                .product(product)
                                .quantity(entry.getValue())
                                .price(product.getPrice())
                                .build();
                    }).collect(Collectors.toList());

            BigDecimal totalAmount = orderItems.stream()
                    .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Order order = Order.builder()
                    .user(user)
                    .totalAmount(totalAmount)
                    .items(orderItems)
                    .build();

            return orderRepositoryPort.save(order);
        } finally {
            lockingPort.releaseLock(lockKey);
        }
    }
}