package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.OrderJpaRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderJpaRepository 단위 테스트")
class OrderJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Order> orderQuery;

    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void setUp() {
        orderJpaRepository = new OrderJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("주문 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 주문 저장")
        void save_NewOrder_Success() {
            // given
            User user = User.builder().id(1L).name("테스트 사용자").build();
            Order order = Order.builder()
                    .user(user)
                    .totalAmount(new BigDecimal("10000"))
                    .createdAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(order);

            // when
            Order savedOrder = orderJpaRepository.save(order);

            // then
            assertThat(savedOrder).isEqualTo(order);
            verify(entityManager, times(1)).persist(order);
        }

        @Test
        @DisplayName("성공케이스: 기존 주문 업데이트")
        void save_ExistingOrder_Success() {
            // given
            User user = User.builder().id(1L).build();
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .totalAmount(new BigDecimal("15000"))
                    .build();

            when(entityManager.merge(order)).thenReturn(order);

            // when
            Order savedOrder = orderJpaRepository.save(order);

            // then
            assertThat(savedOrder).isEqualTo(order);
            verify(entityManager, times(1)).merge(order);
        }
    }

    @Nested
    @DisplayName("사용자별 주문 조회 테스트")
    class FindByUserTests {

        @Test
        @DisplayName("성공케이스: 사용자별 주문 조회")
        void findByUser_Success() {
            // given
            User user = User.builder().id(1L).build();
            List<Order> expectedOrders = Arrays.asList(
                    Order.builder().id(1L).user(user).totalAmount(new BigDecimal("10000")).build(),
                    Order.builder().id(2L).user(user).totalAmount(new BigDecimal("20000")).build()
            );

            when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
            when(orderQuery.setParameter("user", user)).thenReturn(orderQuery);
            when(orderQuery.getResultList()).thenReturn(expectedOrders);

            // when
            List<Order> orders = orderJpaRepository.findByUser(user);

            // then
            assertThat(orders).hasSize(2);
            assertThat(orders).allMatch(o -> o.getUser().equals(user));
            verify(entityManager).createQuery("SELECT o FROM Order o WHERE o.user = :user ORDER BY o.createdAt DESC", Order.class);
        }

        @Test
        @DisplayName("성공케이스: 주문이 없는 사용자")
        void findByUser_EmptyResult() {
            // given
            User user = User.builder().id(999L).build();

            when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
            when(orderQuery.setParameter("user", user)).thenReturn(orderQuery);
            when(orderQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<Order> orders = orderJpaRepository.findByUser(user);

            // then
            assertThat(orders).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID와 사용자로 조회 테스트")
    class FindByIdAndUserTests {

        @Test
        @DisplayName("성공케이스: ID와 사용자로 주문 조회")
        void findByIdAndUser_Success() {
            // given
            Long orderId = 1L;
            User user = User.builder().id(1L).build();
            Order expectedOrder = Order.builder()
                    .id(orderId)
                    .user(user)
                    .totalAmount(new BigDecimal("10000"))
                    .build();

            when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
            when(orderQuery.setParameter("orderId", orderId)).thenReturn(orderQuery);
            when(orderQuery.setParameter("user", user)).thenReturn(orderQuery);
            when(orderQuery.getSingleResult()).thenReturn(expectedOrder);

            // when
            Optional<Order> foundOrder = orderJpaRepository.findByIdAndUser(orderId, user);

            // then
            assertThat(foundOrder).isPresent();
            assertThat(foundOrder.get()).isEqualTo(expectedOrder);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 주문")
        void findByIdAndUser_NotFound() {
            // given
            Long orderId = 999L;
            User user = User.builder().id(1L).build();

            when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
            when(orderQuery.setParameter("orderId", orderId)).thenReturn(orderQuery);
            when(orderQuery.setParameter("user", user)).thenReturn(orderQuery);
            when(orderQuery.getSingleResult()).thenThrow(new RuntimeException());

            // when
            Optional<Order> foundOrder = orderJpaRepository.findByIdAndUser(orderId, user);

            // then
            assertThat(foundOrder).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 주문 조회")
        void findById_Success() {
            // given
            Long orderId = 1L;
            Order expectedOrder = Order.builder()
                    .id(orderId)
                    .totalAmount(new BigDecimal("10000"))
                    .build();

            when(entityManager.find(Order.class, orderId)).thenReturn(expectedOrder);

            // when
            Optional<Order> foundOrder = orderJpaRepository.findById(orderId);

            // then
            assertThat(foundOrder).isPresent();
            assertThat(foundOrder.get()).isEqualTo(expectedOrder);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long orderId = 999L;
            when(entityManager.find(Order.class, orderId)).thenReturn(null);

            // when
            Optional<Order> foundOrder = orderJpaRepository.findById(orderId);

            // then
            assertThat(foundOrder).isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            Order order = Order.builder()
                    .user(User.builder().id(1L).build())
                    .totalAmount(new BigDecimal("10000"))
                    .build();

            doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(order);

            // when & then
            assertThatThrownBy(() -> orderJpaRepository.save(order))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 오류");
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long orderId = 1L;
            when(entityManager.find(Order.class, orderId))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<Order> result = orderJpaRepository.findById(orderId);

            // then
            assertThat(result).isEmpty();
        }
    }
}