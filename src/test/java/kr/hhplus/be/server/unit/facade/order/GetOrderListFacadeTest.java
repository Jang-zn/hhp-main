package kr.hhplus.be.server.unit.facade.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.order.GetOrderListFacade;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetOrderListFacade 단위 테스트")
class GetOrderListFacadeTest {

    @Mock
    private GetOrderListUseCase getOrderListUseCase;
    
    private GetOrderListFacade getOrderListFacade;
    
    private User testUser;
    private List<Order> testOrders;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderListFacade = new GetOrderListFacade(getOrderListUseCase);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        Product product = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("50000"))
            .build();
            
        OrderItem orderItem = OrderItem.builder()
            .product(product)
            .quantity(2)
            .build();
            
        Order order1 = Order.builder()
            .id(1L)
            .user(testUser)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("100000"))
            .items(List.of(orderItem))
            .build();
            
        Order order2 = Order.builder()
            .id(2L)
            .user(testUser)
            .status(OrderStatus.COMPLETED)
            .totalAmount(new BigDecimal("75000"))
            .items(List.of(orderItem))
            .build();
            
        testOrders = List.of(order1, order2);
    }

    @Nested
    @DisplayName("주문 목록 조회")
    class GetOrderList {
        
        @Test
        @DisplayName("성공 - 정상 주문 목록 조회")
        void getOrderList_Success() {
            // given
            Long userId = 1L;
            int limit = 10;
            int offset = 0;
            
            when(getOrderListUseCase.execute(userId)).thenReturn(testOrders);
            
            // when
            List<Order> result = getOrderListFacade.getOrderList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(1).getId()).isEqualTo(2L);
            assertThat(result.get(0).getUser().getId()).isEqualTo(userId);
            
            verify(getOrderListUseCase).execute(userId);
        }
        
        @Test
        @DisplayName("성공 - 빈 주문 목록")
        void getOrderList_EmptyList() {
            // given
            Long userId = 1L;
            int limit = 10;
            int offset = 0;
            
            when(getOrderListUseCase.execute(userId)).thenReturn(List.of());
            
            // when
            List<Order> result = getOrderListFacade.getOrderList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            
            verify(getOrderListUseCase).execute(userId);
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void getOrderList_UserNotFound() {
            // given
            Long userId = 999L;
            int limit = 10;
            int offset = 0;
            
            when(getOrderListUseCase.execute(userId))
                .thenThrow(new UserException.NotFound());
            
            // when & then
            assertThatThrownBy(() -> getOrderListFacade.getOrderList(userId, limit, offset))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(getOrderListUseCase).execute(userId);
        }
        
        @Test
        @DisplayName("성공 - 페이징 처리")
        void getOrderList_WithPaging() {
            // given
            Long userId = 1L;
            int limit = 5;
            int offset = 10;
            
            when(getOrderListUseCase.execute(userId)).thenReturn(List.of(testOrders.get(0)));
            
            // when
            List<Order> result = getOrderListFacade.getOrderList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            
            verify(getOrderListUseCase).execute(userId);
        }
    }
}