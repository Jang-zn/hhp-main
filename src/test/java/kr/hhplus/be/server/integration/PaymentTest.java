package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("결제 API E2E 테스트")
public class PaymentTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;
    @Autowired
    private BalanceRepositoryPort balanceRepositoryPort;
    @Autowired
    private ProductRepositoryPort productRepositoryPort;
    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 설정
        User user = User.builder().id(1L).name("Test User").build();
        userRepositoryPort.save(user);

        // 테스트 잔액 설정 (충분한 잔액)
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("1000000"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        balanceRepositoryPort.save(balance);

        // 테스트 상품 설정
        Product product = Product.builder().id(1L).name("테스트 상품").price(new BigDecimal("50000")).stock(10).reservedStock(0).build();
        productRepositoryPort.save(product);

        // 테스트 주문 설정 (PENDING 상태)
        OrderItem orderItem = OrderItem.builder().product(product).quantity(1).build();
        Order order = Order.builder()
                .id(1L)
                .user(user)
                .totalAmount(new BigDecimal("50000"))
                .items(List.of(orderItem))
                .build();
        orderRepositoryPort.save(order);
    }

    @Test
    @DisplayName("주문 결제 API 테스트")
    void payOrderTest() throws Exception {
        // given
        long orderId = 1L;

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PAID")); // COMPLETED -> PAID
    }
} 