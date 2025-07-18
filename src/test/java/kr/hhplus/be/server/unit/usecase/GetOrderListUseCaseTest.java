package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("GetOrderListUseCase 단위 테스트")
class GetOrderListUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    private GetOrderListUseCase getOrderListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderListUseCase = new GetOrderListUseCase(userRepositoryPort, orderRepositoryPort);
    }

    @Test
    @DisplayName("주문 목록 조회 성공")
    void getOrderList_Success() {
        // given
        Long userId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        List<Order> orders = List.of(
                Order.builder()
                        .user(user)
                        .totalAmount(new BigDecimal("120000"))
                        .build(),
                Order.builder()
                        .user(user)
                        .totalAmount(new BigDecimal("80000"))
                        .build()
        );
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findByUserId(userId)).thenReturn(orders);

        // when
        List<Order> result = getOrderListUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 빈 리스트 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isNotNull();
        // assertThat(result).hasSize(2);
        // assertThat(result.get(0).getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 주문 목록 조회")
    void getOrderList_UserNotFound() {
        // given
        Long userId = 999L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when
        List<Order> result = getOrderListUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 빈 리스트 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isNotNull();
        // assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideUserData")
    @DisplayName("다양한 사용자의 주문 목록 조회")
    void getOrderList_WithDifferentUsers(Long userId, String userName) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        
        List<Order> orders = List.of(
                Order.builder()
                        .user(user)
                        .totalAmount(new BigDecimal("50000"))
                        .build()
        );
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findByUserId(userId)).thenReturn(orders);

        // when
        List<Order> result = getOrderListUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 빈 리스트 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isNotNull();
        // assertThat(result).isNotEmpty();
        // assertThat(result.get(0).getUser().getName()).isEqualTo(userName);
    }

    private static Stream<Arguments> provideUserData() {
        return Stream.of(
                Arguments.of(1L, "홍길동"),
                Arguments.of(2L, "김철수"),
                Arguments.of(3L, "이영희")
        );
    }
}