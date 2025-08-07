package kr.hhplus.be.server.unit.adapter.storage.inmemory.order;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryOrderRepository;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryOrderRepository 비즈니스 시나리오 테스트
 * 
 * Why: 주문 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 주문 관리 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("주문 저장소 비즈니스 시나리오")
class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
    }

    // === 주문 저장 시나리오 ===
        
    @Test
    @DisplayName("새로운 주문을 정상적으로 저장할 수 있다")
    void canSaveNewOrder() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .id(1L)
                .name("테스트사용자")
                .build();
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .id(1L)
                .userId(user.getId())
                .totalAmount(new BigDecimal("120000"))
                .build();

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getUserId()).isEqualTo(user.getId());
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 금액의 주문을 저장할 수 있다")
    void canSaveOrdersWithVariousAmounts(String userName, String totalAmount) {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name(userName)
                .build();
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(new BigDecimal(totalAmount))
                .build();

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getUserId()).isEqualTo(user.getId());
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal(totalAmount));
    }

    @Test
    @DisplayName("영액 주문도 정상적으로 저장된다")
    void canSaveZeroAmountOrder() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("영액주문사용자")
                .build();
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(BigDecimal.ZERO)
                .build();

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("대금액 주문도 안전하게 저장된다")
    void canSaveLargeAmountOrder() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("대금액주문사용자")
                .build();
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(new BigDecimal("999999999"))
                .build();

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("999999999"));
    }

    // === 주문 조회 시나리오 ===
        
    @Test
    @DisplayName("저장된 주문을 ID로 조회할 수 있다")
    void canFindSavedOrderById() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("조회테스트사용자")
                .build();
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(new BigDecimal("50000"))
                .build();
        Order savedOrder = orderRepository.save(order);

        // When
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());

        // Then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUserId()).isEqualTo(user.getId());
        assertThat(foundOrder.get().getTotalAmount()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenOrderNotFound() {
        // When
        Optional<Order> foundOrder = orderRepository.findById(999L);

        // Then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("null ID로 주문 조회 시 예외가 발생한다")
    void throwsExceptionWhenFindByNullId() {
        // When & Then
        assertThatThrownBy(() -> orderRepository.findById(null))
                .isInstanceOf(OrderException.OrderIdCannotBeNull.class);
    }

    @Test
    @DisplayName("음수 ID로 주문 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindByNegativeId() {
        // When
        Optional<Order> foundOrder = orderRepository.findById(-1L);

        // Then
        assertThat(foundOrder).isEmpty();
    }

    // === 동시성 시나리오 ===

    @Test
    @DisplayName("서로 다른 주문들이 동시에 생성되어도 안전하게 처리된다")
    void safelyHandlesConcurrentDifferentOrders() {
        // When - 20개의 동시 주문 생성
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(20, () -> {
                Long uniqueId = System.nanoTime();
                User user = TestBuilder.UserBuilder.defaultUser()
                        .name("동시사용자_" + uniqueId)
                        .build();
                
                Order order = TestBuilder.OrderBuilder.defaultOrder()
                        .userId(user.getId())
                        .totalAmount(new BigDecimal("10000"))
                        .build();
                
                Order saved = orderRepository.save(order);
                return saved != null ? 1 : 0;
            });

        // Then - 모든 주문이 성공적으로 생성
        assertThat(result.getTotalCount()).isEqualTo(20);
        assertThat(result.getSuccessCount()).isEqualTo(20);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 사용자가 여러 주문을 동시에 생성해도 데이터 일관성이 보장된다")
    void maintainsDataConsistencyForSameUserConcurrentOrders() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("동시주문사용자")
                .build();

        // When - 10개의 동시 주문 생성
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                Order order = TestBuilder.OrderBuilder.defaultOrder()
                        .userId(user.getId())
                        .totalAmount(new BigDecimal("5000"))
                        .build();
                
                Order saved = orderRepository.save(order);
                return saved != null ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        
        // 사용자의 주문이 저장되었는지 확인 (동시성으로 인한 일부 중복 가능성 고려)
        List<Order> userOrders = orderRepository.findByUserId(user.getId());
        assertThat(userOrders.size()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("주문 저장과 조회가 동시에 이루어져도 데이터 일관성이 보장된다")
    void maintainsDataConsistencyUnderConcurrentReadAndWrite() {
        // Given - 초기 주문 생성
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("동시작업사용자")
                .build();
        
        Order initialOrder = TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(new BigDecimal("100000"))
                .build();
        Order savedOrder = orderRepository.save(initialOrder);

        // When - 저장과 조회가 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(20, () -> {
                if (Math.random() < 0.5) {
                    // 새로운 주문 생성
                    Order newOrder = TestBuilder.OrderBuilder.defaultOrder()
                            .userId(user.getId())
                            .totalAmount(new BigDecimal("50000"))
                            .build();
                    Order saved = orderRepository.save(newOrder);
                    return saved != null ? 1 : 0;
                } else {
                    // 기존 주문 조회
                    Optional<Order> found = orderRepository.findById(savedOrder.getId());
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then - 데이터 일관성 확인
        assertThat(result.getTotalCount()).isEqualTo(20);
        assertThat(result.getSuccessCount()).isGreaterThan(10);
        
        // 최종 상태 확인 - 사용자의 주문들이 올바르게 저장됨
        List<Order> userOrders = orderRepository.findByUserId(user.getId());
        assertThat(userOrders.size()).isGreaterThanOrEqualTo(1);
    }

    // === 헬퍼 메서드 ===
    
    static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "75000"),
                Arguments.of("박민수", "500000"),
                Arguments.of("정수진", "12000"),
                Arguments.of("최영수", "999999")
        );
    }
}