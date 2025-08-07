package kr.hhplus.be.server.unit.adapter.storage.jpa.order;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.OrderJpaRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
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
/**
 * OrderJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: 주문 데이터 저장소의 비즈니스 로직과 데이터 무결성 보장 검증
 * How: 주문 관리 시나리오를 반영한 JPA 저장소 테스트로 구성
 */
@DisplayName("주문 데이터 저장소 비즈니스 시나리오")
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

    @Test
    @DisplayName("새로운 고객 주문을 성공적으로 저장한다")
    void save_NewOrder_Success() {
        // given - 고객이 새로운 주문을 생성하는 상황
        Order newOrder = TestBuilder.OrderBuilder.defaultOrder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000"))
                .createdAt(LocalDateTime.now())
                .build();

        doNothing().when(entityManager).persist(newOrder);

        // when
        Order savedOrder = orderJpaRepository.save(newOrder);

        // then
        assertThat(savedOrder).isEqualTo(newOrder);
        verify(entityManager, times(1)).persist(newOrder);
    }

    @Test
    @DisplayName("기존 주문 정보를 성공적으로 업데이트한다")
    void save_ExistingOrder_Success() {
        // given - 기존 주문의 상태나 금액 업데이트
        Order existingOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(1L)
                .userId(1L)
                .totalAmount(new BigDecimal("15000"))
                .build();

        when(entityManager.merge(existingOrder)).thenReturn(existingOrder);

        // when
        Order savedOrder = orderJpaRepository.save(existingOrder);

        // then
        assertThat(savedOrder).isEqualTo(existingOrder);
        verify(entityManager, times(1)).merge(existingOrder);
    }

    @Test
    @DisplayName("고객의 주문 내역을 성공적으로 조회한다")
    void findByUser_Success() {
        // given - 고객이 자신의 주문 내역을 확인하는 상황
        Long customerId = 1L;
        List<Order> customerOrders = Arrays.asList(
                TestBuilder.OrderBuilder.defaultOrder().id(1L).userId(customerId).totalAmount(new BigDecimal("10000")).build(),
                TestBuilder.OrderBuilder.defaultOrder().id(2L).userId(customerId).totalAmount(new BigDecimal("20000")).build()
        );

        when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
        when(orderQuery.setParameter("userId", customerId)).thenReturn(orderQuery);
        when(orderQuery.getResultList()).thenReturn(customerOrders);

        // when
        List<Order> orders = orderJpaRepository.findByUserId(customerId);

        // then
        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getUserId().equals(customerId));
        verify(entityManager).createQuery("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC", Order.class);
    }

    @Test
    @DisplayName("주문 내역이 없는 고객에 대해 빈 목록을 반환한다")
    void findByUser_EmptyResult() {
        // given - 아직 주문을 하지 않은 신규 고객
        Long newCustomerId = 999L;

        when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
        when(orderQuery.setParameter("userId", newCustomerId)).thenReturn(orderQuery);
        when(orderQuery.getResultList()).thenReturn(Arrays.asList());

        // when
        List<Order> orders = orderJpaRepository.findByUserId(newCustomerId);

        // then
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("고객이 자신의 특정 주문을 성공적으로 조회한다")
    void findByIdAndUser_Success() {
        // given - 고객이 자신의 주문 상세 정보를 확인하는 상황
        Long orderId = 1L;
        Long customerId = 1L;
        Order customerOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(customerId)
                .totalAmount(new BigDecimal("10000"))
                .build();

        when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
        when(orderQuery.setParameter("orderId", orderId)).thenReturn(orderQuery);
        when(orderQuery.setParameter("userId", customerId)).thenReturn(orderQuery);
        when(orderQuery.getSingleResult()).thenReturn(customerOrder);

        // when
        Optional<Order> foundOrder = orderJpaRepository.findByIdAndUserId(orderId, customerId);

        // then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get()).isEqualTo(customerOrder);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
    void findByIdAndUser_NotFound() {
        // given - 존재하지 않는 주문에 대한 조회 시도
        Long nonExistentOrderId = 999L;
        Long customerId = 1L;

        when(entityManager.createQuery(anyString(), eq(Order.class))).thenReturn(orderQuery);
        when(orderQuery.setParameter("orderId", nonExistentOrderId)).thenReturn(orderQuery);
        when(orderQuery.setParameter("userId", customerId)).thenReturn(orderQuery);
        when(orderQuery.getSingleResult()).thenThrow(new RuntimeException());

        // when
        Optional<Order> foundOrder = orderJpaRepository.findByIdAndUserId(nonExistentOrderId, customerId);

        // then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("주문 ID로 주문 정보를 성공적으로 조회한다")
    void findById_Success() {
        // given - 시스템이 주문 정보를 조회하는 상황
        Long orderId = 1L;
        Order existingOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .totalAmount(new BigDecimal("10000"))
                .build();

        when(entityManager.find(Order.class, orderId)).thenReturn(existingOrder);

        // when
        Optional<Order> foundOrder = orderJpaRepository.findById(orderId);

        // then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get()).isEqualTo(existingOrder);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
    void findById_NotFound() {
        // given - 존재하지 않는 주문 ID
        Long nonExistentOrderId = 999L;
        when(entityManager.find(Order.class, nonExistentOrderId)).thenReturn(null);

        // when
        Optional<Order> foundOrder = orderJpaRepository.findById(nonExistentOrderId);

        // then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("주문 저장 중 데이터베이스 오류 시 예외가 전파된다")
    void save_PersistException() {
        // given - 데이터베이스 오류 상황
        Order newOrder = TestBuilder.OrderBuilder.defaultOrder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000"))
                .build();

        doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(newOrder);

        // when & then
        assertThatThrownBy(() -> orderJpaRepository.save(newOrder))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 오류");
    }

    @Test
    @DisplayName("주문 조회 중 데이터베이스 오류 시 빈 결과를 반환한다")
    void findById_Exception() {
        // given - 데이터베이스 연결 두절 상황
        Long orderId = 1L;
        when(entityManager.find(Order.class, orderId))
                .thenThrow(new RuntimeException("데이터베이스 오류"));

        // when
        Optional<Order> result = orderJpaRepository.findById(orderId);

        // then
        assertThat(result).isEmpty();
    }
}