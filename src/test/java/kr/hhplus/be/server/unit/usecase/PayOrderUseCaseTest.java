package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Payment;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.PayOrderUseCase;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("PayOrderUseCase 단위 테스트")
class PayOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private PayOrderUseCase payOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payOrderUseCase = new PayOrderUseCase(
                null, null, orderRepositoryPort, paymentRepositoryPort,
                null, eventLogRepositoryPort, lockingPort, cachePort, null
        );
    }

    @Test
    @DisplayName("주문 결제 성공")
    void payOrder_Success() {
        // given
        Long orderId = 1L;
        Long userId = 1L;
        Long couponId = 1L;
        
        Order order = Order.builder()
                .totalAmount(new BigDecimal("1200000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
    }

    @ParameterizedTest
    @MethodSource("providePaymentData")
    @DisplayName("다양한 결제 시나리오")
    void payOrder_WithDifferentScenarios(Long orderId, Long userId, Long couponId) {
        // given
        Order order = Order.builder()
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = payOrderUseCase.execute(orderId, userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder()).isEqualTo(order);
    }

    private static Stream<Arguments> providePaymentData() {
        return Stream.of(
                Arguments.of(1L, 1L, 1L), // 쿠폰 사용
                Arguments.of(2L, 2L, null), // 쿠폰 미사용
                Arguments.of(3L, 3L, 2L) // 다른 쿠폰 사용
        );
    }
} 