package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OrderService.getOrderWithDetails 메서드 테스트
 */
@DisplayName("주문 상세 조회 서비스")
class GetOrderWithDetailsTest {

    @Mock
    private GetOrderUseCase getOrderUseCase;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
            null, getOrderUseCase, null, null, null, null, null, null,
            null, null
        );
    }

    @Test
    @DisplayName("정상적인 주문 상세 조회가 성공한다")
    void getOrderWithDetails_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Order expectedOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .build();
        
        when(getOrderUseCase.execute(userId, orderId)).thenReturn(java.util.Optional.of(expectedOrder));
        
        // when
        Order result = orderService.getOrderWithDetails(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        
        verify(getOrderUseCase).execute(userId, orderId);
    }
}