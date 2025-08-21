package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.order.GetOrderListUseCase;
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
import java.util.List;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.ArrayList;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GetOrderListUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 주문 목록 조회의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 고객의 주문 이력 조회 시나리오를 반영한 테스트로 구성
 */
@DisplayName("주문 목록 조회 비즈니스 시나리오")
class GetOrderListUseCaseTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private CachePort cachePort;
    @Mock private KeyGenerator keyGenerator;
    
    private GetOrderListUseCase getOrderListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getOrderListUseCase = new GetOrderListUseCase(userRepositoryPort, orderRepositoryPort, cachePort, keyGenerator);
    }

    @Test
    @DisplayName("고객이 기존 주문 목록을 조회할 수 있다")
    void customerCanViewOrderHistory() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).name("홍길동").build();
        List<Order> orders = List.of(
            TestBuilder.OrderBuilder.defaultOrder()
                .id(1L).userId(customer.getId()).totalAmount(new BigDecimal("120000")).build(),
            TestBuilder.OrderBuilder.defaultOrder()
                .id(2L).userId(customer.getId()).totalAmount(new BigDecimal("80000")).build()
        );
        
        when(userRepositoryPort.existsById(customer.getId())).thenReturn(true);
        when(orderRepositoryPort.findByUserId(eq(customer.getId()), any(Pageable.class))).thenReturn(orders);

        // When
        List<Order> result = getOrderListUseCase.execute(customer.getId());

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotalAmount()).isEqualTo(new BigDecimal("120000"));
        assertThat(result.get(1).getTotalAmount()).isEqualTo(new BigDecimal("80000"));
        
        verify(orderRepositoryPort).findByUserId(eq(customer.getId()), any(Pageable.class));
    }

    @Test
    @DisplayName("데이터베이스에서 주문 목록을 안정적으로 조회할 수 있다")
    void canReliablyRetrieveOrdersFromDatabase() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<Order> dbOrders = List.of(
            TestBuilder.OrderBuilder.defaultOrder()
                .id(1L).userId(customer.getId()).totalAmount(new BigDecimal("150000")).build()
        );
        
        when(userRepositoryPort.existsById(customer.getId())).thenReturn(true);
        when(orderRepositoryPort.findByUserId(eq(customer.getId()), any(Pageable.class))).thenReturn(dbOrders);

        // When
        List<Order> result = getOrderListUseCase.execute(customer.getId());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualTo(new BigDecimal("150000"));
        
        verify(orderRepositoryPort).findByUserId(eq(customer.getId()), any(Pageable.class));
    }

    @Test
    @DisplayName("주문 이력이 없는 신규 고객은 빈 목록을 조회할 수 있다")
    void newCustomerReceivesEmptyOrderList() {
        // Given
        User newCustomer = TestBuilder.UserBuilder.defaultUser()
            .id(1L).name("신규고객").build();
        
        when(userRepositoryPort.existsById(newCustomer.getId())).thenReturn(true);
        when(orderRepositoryPort.findByUserId(eq(newCustomer.getId()), any(Pageable.class))).thenReturn(Collections.emptyList());

        // When
        List<Order> result = getOrderListUseCase.execute(newCustomer.getId());

        // Then
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideDiverseCustomerData")
    @DisplayName("다양한 주문 이력을 보유한 고객들이 각자의 목록을 조회할 수 있다")
    void diverseCustomersCanViewTheirOrderHistory(String customerName, int orderCount) {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser()
            .id((long)customerName.hashCode()).name(customerName).build();
        
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= orderCount; i++) {
            orders.add(TestBuilder.OrderBuilder.defaultOrder()
                .id((long) i).userId(customer.getId())
                .totalAmount(new BigDecimal("50000").multiply(BigDecimal.valueOf(i)))
                .build());
        }
        
        when(userRepositoryPort.existsById(customer.getId())).thenReturn(true);
        when(orderRepositoryPort.findByUserId(eq(customer.getId()), any(Pageable.class))).thenReturn(orders);

        // When
        List<Order> result = getOrderListUseCase.execute(customer.getId());

        // Then
        assertThat(result).hasSize(orderCount);
        if (orderCount > 0) {
            assertThat(result.get(0).getUserId()).isEqualTo(customer.getId());
        }
    }

    @Test
    @DisplayName("존재하지 않는 고객의 주문 조회 요청은 차단된다")
    void preventsOrderViewingForNonExistentCustomer() {
        // Given
        Long nonExistentCustomerId = 999L;
        when(userRepositoryPort.existsById(nonExistentCustomerId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> getOrderListUseCase.execute(nonExistentCustomerId))
            .isInstanceOf(UserException.NotFound.class)
            .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
            
        verify(orderRepositoryPort, never()).findByUserId(any(Long.class), any(Pageable.class));
    }


    @Test
    @DisplayName("서로 다른 고객들이 동시에 주문 목록을 조회할 수 있다")
    void multipleDifferentCustomersCanViewOrdersSimultaneously() {
        // Given
        int numberOfCustomers = 10;
        
        for (int i = 1; i <= numberOfCustomers; i++) {
            Long customerId = (long) i;
            List<Order> orders = List.of(
                TestBuilder.OrderBuilder.defaultOrder()
                    .id(customerId).userId(customerId)
                    .totalAmount(new BigDecimal("100000"))
                    .build()
            );
            
            when(userRepositoryPort.existsById(customerId)).thenReturn(true);
            when(orderRepositoryPort.findByUserId(eq(customerId), any(Pageable.class))).thenReturn(orders);
        }
        
        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfCustomers, () -> {
                Long customerId = (long)(System.nanoTime() % numberOfCustomers + 1);
                List<Order> orders = getOrderListUseCase.execute(customerId);
                
                assertThat(orders).hasSize(1);
                assertThat(orders.get(0).getUserId()).isEqualTo(customerId);
                return orders;
            });
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfCustomers);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 고객이 여러 번 동시에 주문 목록을 조회해도 안정적으로 처리된다")
    void sameCustomerCanViewOrdersMultipleTimesConcurrently() {
        // Given
        User customer = TestBuilder.UserBuilder.defaultUser().id(1L).build();
        List<Order> orders = List.of(
            TestBuilder.OrderBuilder.defaultOrder()
                .id(1L).userId(customer.getId()).totalAmount(new BigDecimal("100000")).build(),
            TestBuilder.OrderBuilder.defaultOrder()
                .id(2L).userId(customer.getId()).totalAmount(new BigDecimal("200000")).build()
        );
        
        when(userRepositoryPort.existsById(customer.getId())).thenReturn(true);
        when(orderRepositoryPort.findByUserId(eq(customer.getId()), any(Pageable.class))).thenReturn(orders);
        
        // When
        int numberOfRequests = 5;
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(numberOfRequests, () -> {
                List<Order> customerOrders = getOrderListUseCase.execute(customer.getId());
                
                assertThat(customerOrders).hasSize(2);
                assertThat(customerOrders.get(0).getUserId()).isEqualTo(customer.getId());
                return customerOrders;
            });
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(numberOfRequests);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }
    
    // === 테스트 데이터 제공자 ===
    
    private static Stream<Arguments> provideDiverseCustomerData() {
        return Stream.of(
            Arguments.of("홍길동", 2),
            Arguments.of("김철수", 1),
            Arguments.of("이영희", 3),
            Arguments.of("박영수", 0)
        );
    }
    
}