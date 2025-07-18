package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.OrderException;
import kr.hhplus.be.server.domain.exception.ProductException;
import java.util.Collections;

@DisplayName("CreateOrderUseCase 단위 테스트")
class CreateOrderUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    
    @Mock
    private EventLogRepositoryPort eventLogRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderUseCase = new CreateOrderUseCase(
                userRepositoryPort, productRepositoryPort, orderRepositoryPort,
                eventLogRepositoryPort, lockingPort, cachePort
        );
    }

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 2, 2L, 1);
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product1 = Product.builder()
                .name("노트북")
                .price(new BigDecimal("1200000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        Product product2 = Product.builder()
                .name("스마트폰")
                .price(new BigDecimal("800000"))
                .stock(20)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepositoryPort.findById(2L)).thenReturn(Optional.of(product2));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = createOrderUseCase.execute(userId, productQuantities);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
    }

    @ParameterizedTest
    @MethodSource("provideOrderData")
    @DisplayName("다양한 상품 조합으로 주문 생성")
    void createOrder_WithDifferentProducts(Long userId, Map<Long, Integer> productQuantities) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = createOrderUseCase.execute(userId, productQuantities);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 주문 생성 시 예외 발생")
    void createOrder_UserNotFound() {
        // given
        Long userId = 999L;
        Map<Long, Integer> productQuantities = Map.of(1L, 1);
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(OrderException.InvalidUser.class)
                .hasMessage("Invalid user ID");
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 주문 생성 시 예외 발생")
    void createOrder_ProductNotFound() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(999L, 1);
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.NotFound.class)
                .hasMessage("Product not found");
    }

    @Test
    @DisplayName("재고 부족 상품으로 주문 생성 시 예외 발생")
    void createOrder_InsufficientStock() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 100); // 재고보다 많은 주문
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("재고 부족 상품")
                .price(new BigDecimal("10000"))
                .stock(10) // 주문량보다 적은 재고
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class)
                .hasMessage("Product out of stock");
    }

    @Test
    @DisplayName("빈 주문 리스트로 주문 생성 시 예외 발생")
    void createOrder_EmptyProductList() {
        // given
        Long userId = 1L;
        Map<Long, Integer> emptyProductQuantities = Collections.emptyMap();
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, emptyProductQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order must contain at least one item");
    }

    @Test
    @DisplayName("null 사용자 ID로 주문 생성 시 예외 발생")
    void createOrder_WithNullUserId() {
        // given
        Long userId = null;
        Map<Long, Integer> productQuantities = Map.of(1L, 1);

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 주문 리스트로 주문 생성 시 예외 발생")
    void createOrder_WithNullProductList() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = null;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 수량으로 주문 생성 시 예외 발생")
    void createOrder_WithNegativeQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, -1); // 음수 수량
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    @DisplayName("0 수량으로 주문 생성 시 예외 발생")
    void createOrder_WithZeroQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 0); // 0 수량
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    @DisplayName("대량 주문 생성 시 예외 발생")
    void createOrder_ExcessiveQuantity() {
        // given
        Long userId = 1L;
        Map<Long, Integer> productQuantities = Map.of(1L, 1000000); // 대량 주문
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Product product = Product.builder()
                .name("상품")
                .price(new BigDecimal("10000"))
                .stock(100)
                .reservedStock(0)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, productQuantities))
                .isInstanceOf(ProductException.OutOfStock.class)
                .hasMessage("Product out of stock");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("다양한 비정상 사용자 ID로 주문 생성")
    void createOrder_WithInvalidUserIds(Long invalidUserId) {
        // given
        Map<Long, Integer> productQuantities = Map.of(1L, 1);
        
        when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createOrderUseCase.execute(invalidUserId, productQuantities))
                .isInstanceOf(OrderException.InvalidUser.class);
    }

    private static Stream<Arguments> provideOrderData() {
        return Stream.of(
                Arguments.of(1L, Map.of(1L, 1)), // 단일 상품
                Arguments.of(2L, Map.of(1L, 2, 2L, 1)), // 다중 상품
                Arguments.of(3L, Map.of(3L, 5)) // 대량 주문
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }
} 