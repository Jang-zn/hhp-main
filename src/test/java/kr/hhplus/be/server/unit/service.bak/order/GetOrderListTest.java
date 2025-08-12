package kr.hhplus.be.server.unit.service.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.service.OrderService;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OrderService.getOrderList 메서드 테스트
 */
@DisplayName("주문 목록 조회 서비스")
class GetOrderListTest {

    @Mock
    private GetOrderListUseCase getOrderListUseCase;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
            null, null, getOrderListUseCase, null, null, null, null, null,
            null, null
        );
    }

    @Test
    @DisplayName("정상적인 주문 목록 조회가 성공한다")
    void getOrderList_Success() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        List<Order> expectedOrders = List.of(
            TestBuilder.OrderBuilder.defaultOrder().userId(userId).build(),
            TestBuilder.OrderBuilder.defaultOrder().userId(userId).build()
        );
        
        when(getOrderListUseCase.execute(userId)).thenReturn(expectedOrders);
        
        // when
        List<Order> result = orderService.getOrderList(userId, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(1).getUserId()).isEqualTo(userId);
        
        verify(getOrderListUseCase).execute(userId);
    }
    
    @Test
    @DisplayName("빈 주문 목록 조회가 성공한다")
    void getOrderList_EmptyList() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        when(getOrderListUseCase.execute(userId)).thenReturn(List.of());
        
        // when
        List<Order> result = orderService.getOrderList(userId, limit, offset);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(getOrderListUseCase).execute(userId);
    }
}