package kr.hhplus.be.server.unit.usecase.order;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.usecase.order.CreateOrderUseCase;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private CachePort cachePort;

    private CreateOrderUseCase createOrderUseCase;

    private User testUser;
    private Product testProduct;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createOrderUseCase = new CreateOrderUseCase(
            userRepositoryPort,
            productRepositoryPort, 
            orderRepositoryPort,
            eventLogRepositoryPort,
            cachePort
        );
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(new BigDecimal("50000"))
            .stock(100)
            .build();
            
        testOrder = mock(Order.class);
        when(testOrder.getId()).thenReturn(1L);
    }
}