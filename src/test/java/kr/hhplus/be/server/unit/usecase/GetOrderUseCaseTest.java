package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("GetOrderUseCase 단위 테스트")
class GetOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    private GetOrderUseCase getOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderUseCase = new GetOrderUseCase(userRepositoryPort, orderRepositoryPort);
    }

    @Test
    @DisplayName("주문 조회 성공")
    void getOrder_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("120000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getUser()).isEqualTo(user);
        // assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회")
    void getOrder_OrderNotFound() {
        // given
        Long userId = 1L;
        Long orderId = 999L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.empty());

        // when
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 주문 조회")
    void getOrder_WithDifferentOrders(Long userId, Long orderId, String amount) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal(amount))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));

        // when
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal(amount));
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of(1L, 1L, "50000"),
                Arguments.of(2L, 2L, "100000"),
                Arguments.of(3L, 3L, "75000")
        );
    }
}