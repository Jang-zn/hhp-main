package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.usecase.order.GetOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GetOrderUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 주문 조회 유스케이스의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 주문 조회 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("주문 조회 유스케이스 비즈니스 시나리오")
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

    // === 기본 주문 조회 시나리오 ===
        
    @Test
    @DisplayName("정상적인 주문 조회가 성공적으로 수행된다")
    void canRetrieveOrderSuccessfully() {
        // Given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(userId)
                .totalAmount(new BigDecimal("120000"))
                .build();
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(orderRepositoryPort.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        // When
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(userId);
        assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 주문 조회 시 예외가 발생한다")
    void throwsExceptionWhenUserNotFound() {
        // Given
        Long userId = 999L;
        Long orderId = 1L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getOrderUseCase.execute(userId, orderId))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenOrderNotFound() {
        // Given
        Long userId = 1L;
        Long orderId = 999L;
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(orderRepositoryPort.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        // When
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 파라미터로 주문 조회 시 예외가 발생한다")
    void throwsExceptionForNullParameters() {
        // When & Then - null userId
        assertThatThrownBy(() -> getOrderUseCase.execute(null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
                
        // When & Then - null orderId
        assertThatThrownBy(() -> getOrderUseCase.execute(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OrderId cannot be null");
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 금액의 주문을 조회할 수 있다")
    void canRetrieveOrdersWithVariousAmounts(Long userId, Long orderId, String amount) {
        // Given
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(userId)
                .totalAmount(new BigDecimal(amount))
                .build();
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(orderRepositoryPort.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        // When
        Optional<Order> result = getOrderUseCase.execute(userId, orderId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTotalAmount()).isEqualTo(new BigDecimal(amount));
    }


    // === 동시성 시나리오 ===
        
    @Test
    @DisplayName("여러 스레드에서 동일한 주문을 동시 조회해도 안전하게 처리된다")
    void safelyHandlesConcurrentOrderRetrieval() {
        // Given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = TestBuilder.OrderBuilder.defaultOrder()
                .userId(userId)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(orderRepositoryPort.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        
        // When - 10개의 동시 조회 작업
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                Optional<Order> orderResult = getOrderUseCase.execute(userId, orderId);
                return orderResult.isPresent() ? 1 : 0;
            });
        
        // Then
        assertThat(result.getTotalCount()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("다중 사용자의 주문을 동시 조회해도 데이터 일관성이 보장된다")
    void maintainsDataConsistencyForMultipleUsersConcurrentRetrieval() {
        // Given - 5명의 사용자와 각각의 주문
        for (int i = 1; i <= 5; i++) {
            Long userId = (long) i;
            Long orderId = (long) i;
            
            Order order = TestBuilder.OrderBuilder.defaultOrder()
                    .userId(userId)
                    .totalAmount(new BigDecimal(String.valueOf(100000 * i)))
                    .build();
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(orderRepositoryPort.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        }
        
        // When - 10개의 동시 조회 작업 (다양한 사용자)
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                Long userId = (long) (Math.random() * 5 + 1);
                Long orderId = userId; // 사용자 ID와 동일
                Optional<Order> orderResult = getOrderUseCase.execute(userId, orderId);
                return orderResult.isPresent() ? 1 : 0;
            });
        
        // Then
        assertThat(result.getTotalCount()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }
    
    // === 헬퍼 메서드 ===
    
    static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of(1L, 1L, "50000"),
                Arguments.of(2L, 2L, "100000"),
                Arguments.of(3L, 3L, "75000"),
                Arguments.of(4L, 4L, "150000"),
                Arguments.of(5L, 5L, "25000")
        );
    }

}