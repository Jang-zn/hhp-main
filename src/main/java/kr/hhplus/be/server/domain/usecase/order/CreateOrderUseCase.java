package kr.hhplus.be.server.domain.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.dto.ProductQuantityDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrderUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;

    /**
     * 주문을 생성하고 상품 재고를 예약
     * @param userId 주문하는 사용자 ID
     * @param productQuantities 주문할 상품과 수량 정보
     * @return 생성된 주문 엔티티
     */
    public Order execute(Long userId, List<ProductQuantityDto> productQuantities) {
        log.debug("주문 생성 요청: userId={}, products={}", userId, productQuantities);
        
        try {
            validateParameters(userId, productQuantities);
            if (!userRepositoryPort.existsById(userId)) {
                log.warn("존재하지 않는 사용자: userId={}", userId);
                throw new UserException.NotFound();
            }

            List<Long> productIds = productQuantities.stream()
                    .map(ProductQuantityDto::getProductId)
                    .collect(Collectors.toList());
            
            List<Product> products = productRepositoryPort.findByIds(productIds);
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, product -> product));
            
            List<OrderItem> orderItems = productQuantities.stream()
                    .map(productQuantity -> {
                        Long productId = productQuantity.getProductId();
                        Integer quantity = productQuantity.getQuantity();
                        
                        Product product = productMap.get(productId);
                        if (product == null) {
                            log.warn("존재하지 않는 상품: productId={}", productId);
                            throw new ProductException.NotFound();
                        }
                        
                        product.reserveStock(quantity);
                        productRepositoryPort.save(product);
                        
                        log.debug("재고 예약 완료: productId={}, quantity={}, availableStock={}", 
                                productId, quantity, product.getStock() - product.getReservedStock());
                        
                        return OrderItem.builder()
                                .productId(productId)
                                .quantity(quantity)
                                .price(product.getPrice())
                                .build();
                    }).collect(Collectors.toList());

            BigDecimal totalAmount = orderItems.stream()
                    .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Order order = Order.builder()
                    .userId(userId)
                    .totalAmount(totalAmount)
                    .build();

            Order savedOrder = orderRepositoryPort.save(order);
            
            List<OrderItem> orderItemsWithOrderId = orderItems.stream()
                    .map(item -> item.withOrderId(savedOrder.getId()))
                    .collect(Collectors.toList());
            
            orderItemRepositoryPort.saveAll(orderItemsWithOrderId);
            
            log.debug("OrderItem 배치 저장 완료: orderId={}, itemCount={}", 
                    savedOrder.getId(), orderItemsWithOrderId.size());
            
            log.info("주문 생성 완료: orderId={}, userId={}, totalAmount={}, itemCount={}", 
                    savedOrder.getId(), userId, totalAmount, orderItems.size());
            
            return savedOrder;
        } catch (Exception e) {
            log.error("주문 생성 중 오류 발생: userId={}", userId, e);
            throw e;
        }
    }

    private void validateParameters(Long userId, List<ProductQuantityDto> productQuantities) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new OrderException.EmptyItems();
        }
        
    }
}