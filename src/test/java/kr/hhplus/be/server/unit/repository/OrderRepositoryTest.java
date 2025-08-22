package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("주문 데이터 저장소 비즈니스 시나리오")
class OrderRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Test
    @DisplayName("새로운 주문을 저장할 수 있다")
    void canSaveNewOrder() {
        // Given
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(1L)
                .build();

        // When
        Order savedOrder = orderRepositoryPort.save(order);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Order foundOrder = testEntityManager.find(Order.class, savedOrder.getId());
        assertThat(foundOrder).isNotNull();
        assertThat(foundOrder.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ID로 주문을 조회할 수 있다")
    void canFindOrderById() {
        // Given
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(1L)
                .build();
        Order savedOrder = testEntityManager.persistAndFlush(order);
        testEntityManager.clear();

        // When
        Optional<Order> foundOrder = orderRepositoryPort.findById(savedOrder.getId());

        // Then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용자별 주문을 조회할 수 있다")
    void canFindOrdersByUserId() {
        // Given
        Long userId = 1L;
        for (int i = 0; i < 3; i++) {
            Order order = TestBuilder.OrderBuilder.defaultOrder()
                    .userId(userId)
                    .build();
            testEntityManager.persistAndFlush(order);
        }
        testEntityManager.clear();

        // When
        List<Order> orders = orderRepositoryPort.findByUserId(userId);

        // Then
        assertThat(orders).hasSize(3);
        assertThat(orders).allMatch(o -> o.getUserId().equals(userId));
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenOrderNotFoundById() {
        // Given
        Long nonExistentId = 999L;

        // When
        Optional<Order> foundOrder = orderRepositoryPort.findById(nonExistentId);

        // Then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("null 주문 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullOrder() {
        // When & Then
        assertThatThrownBy(() -> orderRepositoryPort.save(null))
                .isInstanceOf(Exception.class);
    }
}