package kr.hhplus.be.server.domain.facade.order;

import kr.hhplus.be.server.domain.dto.OrderWithDetailsDto;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.domain.port.storage.OrderItemRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 상세 정보 조회 파사드
 * OrderItem과 Product 정보를 매핑하여 완전한 주문 상세 정보를 제공
 */
@Component
public class GetOrderWithDetailsFacade {

    private final GetOrderFacade getOrderFacade;
    private final GetOrderListFacade getOrderListFacade;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;

    public GetOrderWithDetailsFacade(
            GetOrderFacade getOrderFacade,
            GetOrderListFacade getOrderListFacade,
            OrderItemRepositoryPort orderItemRepositoryPort,
            ProductRepositoryPort productRepositoryPort) {
        this.getOrderFacade = getOrderFacade;
        this.getOrderListFacade = getOrderListFacade;
        this.orderItemRepositoryPort = orderItemRepositoryPort;
        this.productRepositoryPort = productRepositoryPort;
    }

    /**
     * 단일 주문의 상세 정보를 조회
     */
    @Transactional(readOnly = true)
    public OrderWithDetailsDto getOrderWithDetails(Long orderId, Long userId) {
        // 1. 기존 GetOrderFacade를 통해 주문 조회 (권한 확인 포함)
        Order order = getOrderFacade.getOrder(orderId, userId);
        if (order == null) {
            throw new kr.hhplus.be.server.domain.exception.OrderException.NotFound();
        }
        
        // 2. 주문 아이템 상세 정보 조회 및 매핑
        List<OrderWithDetailsDto.OrderItemDetailDto> itemDetails = getOrderItemDetails(orderId);
        
        return new OrderWithDetailsDto(order, itemDetails);
    }

    /**
     * 사용자의 모든 주문 목록을 상세 정보와 함께 조회
     */
    @Transactional(readOnly = true)
    public List<OrderWithDetailsDto> getUserOrdersWithDetails(Long userId) {
        // 1. 기존 GetOrderListFacade를 통해 주문 목록 조회
        List<Order> orders = getOrderListFacade.getOrderList(userId, 0, 0);
        
        // 2. 각 주문에 대해 상세 정보 조회 및 매핑
        return orders.stream()
                .map(order -> {
                    List<OrderWithDetailsDto.OrderItemDetailDto> itemDetails = getOrderItemDetails(order.getId());
                    return new OrderWithDetailsDto(order, itemDetails);
                })
                .collect(Collectors.toList());
    }

    /**
     * 주문 ID로 OrderItem 상세 정보를 조회하고 Product 정보와 매핑
     */
    private List<OrderWithDetailsDto.OrderItemDetailDto> getOrderItemDetails(Long orderId) {
        List<OrderItem> orderItems = orderItemRepositoryPort.findByOrderId(orderId);
        
        return orderItems.stream()
                .map(orderItem -> {
                    // Product 정보 조회
                    Product product = productRepositoryPort.findById(orderItem.getProductId())
                            .orElseThrow(() -> new ProductException.NotFound());
                    
                    return new OrderWithDetailsDto.OrderItemDetailDto(
                            orderItem.getProductId(),
                            product.getName(),
                            orderItem.getQuantity(),
                            orderItem.getPrice()
                    );
                })
                .collect(Collectors.toList());
    }

}