package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.adapter.storage.jpa.OrderJpaRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("주문 데이터 저장소 비즈니스 시나리오")
class OrderJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestEntityManager testEntityManager;
    
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void setUp() {
        orderJpaRepository = new OrderJpaRepository(testEntityManager.getEntityManager());
    }

    @Test
    @DisplayName("새로운 주문을 저장할 수 있다")
    void canSaveNewOrder() {
        // Given
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(1L)
                .build();

        // When
        Order savedOrder = orderJpaRepository.save(order);
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
        Optional<Order> foundOrder = orderJpaRepository.findById(savedOrder.getId());

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
        List<Order> orders = orderJpaRepository.findByUserId(userId);

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
        Optional<Order> foundOrder = orderJpaRepository.findById(nonExistentId);

        // Then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("null 주문 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullOrder() {
        // When & Then
        assertThatThrownBy(() -> orderJpaRepository.save(null))
                .isInstanceOf(Exception.class);
    }
}