package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryOrderRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryOrderRepository 단위 테스트")
class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
    }

    @Test
    @DisplayName("주문 저장 성공")
    void save_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("120000"))
                .build();

        // when
        Order savedOrder = orderRepository.save(order);

        // then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getUser()).isEqualTo(user);
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("주문 ID로 조회 성공")
    void findById_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .build();
        Order savedOrder = orderRepository.save(order);

        // when
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());

        // then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUser()).isEqualTo(user);
        assertThat(foundOrder.get().getTotalAmount()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회")
    void findById_NotFound() {
        // when
        Optional<Order> foundOrder = orderRepository.findById(999L);

        // then
        assertThat(foundOrder).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 주문 데이터로 저장")
    void save_WithDifferentOrderData(String userName, String totalAmount) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal(totalAmount))
                .build();

        // when
        Order savedOrder = orderRepository.save(order);

        // then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getUser().getName()).isEqualTo(userName);
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal(totalAmount));
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "75000")
        );
    }
}