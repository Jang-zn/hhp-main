package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.order.ValidateOrderUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ValidateOrderUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 주문 검증 유스케이스의 비즈니스 규칙과 보안 요구사항 충족 검증
 * How: 주문 검증 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("주문 검증 유스케이스 비즈니스 시나리오")
class ValidateOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;
    
    private ValidateOrderUseCase validateOrderUseCase;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validateOrderUseCase = new ValidateOrderUseCase(orderRepositoryPort, paymentRepositoryPort);
    }

    @Test
    @DisplayName("유효한 주문에 대한 검증이 성공한다")
    void execute_ValidOrder_Success() {
        // given - 결제 대기 상태의 유효한 주문
        Long orderId = 1L;
        Long userId = 1L;
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(userId)
                .name("테스트고객")
                .build();
        Order pendingOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(customer));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        
        // when
        Order result = validateOrderUseCase.execute(orderId, userId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
    
    @Test
    @DisplayName("존재하지 않는 주문에 대한 검증 시 예외가 발생한다")
    void execute_OrderNotFound_ThrowsException() {
        // given - 존재하지 않는 주문 ID로 검증 시도
        Long nonExistentOrderId = 999L;
        Long userId = 1L;
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(userId)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(customer));
        when(orderRepositoryPort.findById(nonExistentOrderId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(nonExistentOrderId, userId))
            .isInstanceOf(OrderException.NotFound.class);
    }
    
    @Test
    @DisplayName("다른 사용자의 주문에 대한 검증 시 권한 예외가 발생한다")
    void execute_OrderOwnerMismatch_ThrowsException() {
        // given - 사용자 A가 사용자 B의 주문에 접근 시도
        Long orderId = 1L;
        Long requestingUserId = 1L;
        Long orderOwnerUserId = 2L;
        
        User requestingUser = TestBuilder.UserBuilder.defaultUser()
                .id(requestingUserId)
                .name("요청사용자")
                .build();
        Order otherUserOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(orderOwnerUserId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50000"))
                .build();
        
        when(userRepositoryPort.findById(requestingUserId)).thenReturn(Optional.of(requestingUser));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(otherUserOrder));
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, requestingUserId))
            .isInstanceOf(OrderException.Unauthorized.class);
    }
    
    @Test
    @DisplayName("이미 결제 완료된 주문에 대한 중복 결제 시도 시 예외가 발생한다")
    void execute_AlreadyPaidOrder_ThrowsException() {
        // given - 이미 결제 완료된 주문에 대한 중복 결제 시도
        Long orderId = 1L;
        Long userId = 1L;
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(userId)
                .build();
        Order completedOrder = TestBuilder.OrderBuilder.defaultOrder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("50000"))
                .build();
        Payment existingPayment = TestBuilder.PaymentBuilder.defaultPayment()
                .orderId(orderId)
                .userId(userId)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(customer));
        when(orderRepositoryPort.findById(orderId)).thenReturn(Optional.of(completedOrder));
        when(paymentRepositoryPort.findByOrderId(orderId)).thenReturn(List.of(existingPayment));
        
        // when & then
        assertThatThrownBy(() -> validateOrderUseCase.execute(orderId, userId))
            .isInstanceOf(OrderException.AlreadyPaid.class);
    }
}