package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryOrderRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("주문 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 주문 저장")
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

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryOrderRepositoryTest#provideOrderData")
        @DisplayName("성공케이스: 다양한 주문 데이터로 저장")
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

        @Test
        @DisplayName("성공케이스: 영액 주문 저장")
        void save_ZeroAmountOrder() {
            // given
            User user = User.builder()
                    .name("영액 주문 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(BigDecimal.ZERO)
                    .build();

            // when
            Order savedOrder = orderRepository.save(order);

            // then
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("성공케이스: 대금액 주문 저장")
        void save_LargeAmountOrder() {
            // given
            User user = User.builder()
                    .name("대금액 주문 사용자")
                    .build();
            
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("999999999"))
                    .build();

            // when
            Order savedOrder = orderRepository.save(order);

            // then
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("999999999"));
        }
    }

    @Nested
    @DisplayName("주문 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 주문 ID로 조회")
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
        @DisplayName("실패케이스: 존재하지 않는 주문 조회")
        void findById_NotFound() {
            // when
            Optional<Order> foundOrder = orderRepository.findById(999L);

            // then
            assertThat(foundOrder).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null ID로 주문 조회")
        void findById_WithNullId() {
            // when
            Optional<Order> foundOrder = orderRepository.findById(null);

            // then
            assertThat(foundOrder).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: 음수 ID로 주문 조회")
        void findById_WithNegativeId() {
            // when
            Optional<Order> foundOrder = orderRepository.findById(-1L);

            // then
            assertThat(foundOrder).isEmpty();
        }
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "75000")
        );
    }
}