package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.enums.PaymentStatus;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.PaymentRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("결제 API 통합 테스트")
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
    @Autowired
    private PaymentRepositoryPort paymentRepositoryPort;

    private User testUser;
    private Product testProduct;
    private Order pendingOrder;
    private Order paidOrder;
    private Balance userBalance;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 설정
        testUser = userRepositoryPort.save(User.builder().name("Test User").build());

        // 테스트 잔액 설정 (충분한 잔액)
        userBalance = balanceRepositoryPort.save(Balance.builder()
                .user(testUser)
                .amount(new BigDecimal("1000000"))
                .build());

        // 테스트 상품 설정 (재고가 이미 예약된 상태로 설정)
        testProduct = productRepositoryPort.save(Product.builder().name("테스트 상품").price(new BigDecimal("50000")).stock(10).reservedStock(1).build());

        // 테스트 주문 설정 (PENDING 상태)
        OrderItem orderItem = OrderItem.builder().product(testProduct).quantity(1).price(testProduct.getPrice()).build();
        pendingOrder = orderRepositoryPort.save(Order.builder()
                .user(testUser)
                .totalAmount(new BigDecimal("50000"))
                .items(List.of(orderItem))
                .status(OrderStatus.PENDING)
                .build());

        // 이미 결제된 주문 설정
        paidOrder = orderRepositoryPort.save(Order.builder()
                .user(testUser)
                .totalAmount(new BigDecimal("30000"))
                .items(List.of(OrderItem.builder().product(testProduct).quantity(1).price(testProduct.getPrice()).build()))
                .status(OrderStatus.PAID)
                .build());
        paymentRepositoryPort.save(kr.hhplus.be.server.domain.entity.Payment.builder()
                .order(paidOrder)
                .user(testUser)
                .amount(new BigDecimal("30000"))
                .status(PaymentStatus.PAID)
                .build());
    }

    @Nested
    @DisplayName("POST /api/order/{orderId}/pay - 주문 결제")
    class PayOrder {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("정상적인 주문 결제 요청 시 200 OK와 함께 결제 정보를 반환한다")
            void payOrder_Success() throws Exception {
                // given
                long orderId = pendingOrder.getId();
                OrderRequest request = new OrderRequest(testUser.getId(), null);

                // when & then
                mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.orderId").value(orderId))
                        .andExpect(jsonPath("$.data.status").value("PAID"));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 주문 ID로 결제 요청 시 404 Not Found를 반환한다")
            void payOrder_OrderNotFound_ShouldFail() throws Exception {
                // given
                long nonExistentOrderId = 999L;

                // when & then
                OrderRequest request = new OrderRequest(testUser.getId(), null);
                mockMvc.perform(post("/api/order/{orderId}/pay", nonExistentOrderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // OrderException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_NOT_FOUND.getMessage()));
            }

            @Test
            @DisplayName("이미 결제된 주문으로 결제 요청 시 400 Bad Request를 반환한다")
            void payOrder_AlreadyPaid_ShouldFail() throws Exception {
                // given
                long orderId = paidOrder.getId();
                OrderRequest request = new OrderRequest(testUser.getId(), null);

                // when & then
                mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest()) // OrderException.AlreadyPaid는 400 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_ALREADY_PAID.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_ALREADY_PAID.getMessage()));
            }

            @Test
            @DisplayName("잔액이 부족할 경우 결제 요청 시 402 Payment Required를 반환한다")
            void payOrder_InsufficientBalance_ShouldFail() throws Exception {
                // given
                // 기존 잔액을 부족하게 수정
                userBalance.subtractAmount(new BigDecimal("990000")); // 1000000 - 990000 = 10000 (주문 금액 50000보다 적게)
                balanceRepositoryPort.save(userBalance);

                long orderId = pendingOrder.getId();
                OrderRequest request = new OrderRequest(testUser.getId(), null);

                // when & then
                mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isPaymentRequired()) // BalanceException.InsufficientBalance는 402 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_BALANCE.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.INSUFFICIENT_BALANCE.getMessage()));
            }

            @Test
            @DisplayName("상품 재고가 부족할 경우 결제 요청 시 409 Conflict를 반환한다")
            void payOrder_OutOfStock_ShouldFail() throws Exception {
                // given
                // 재고가 부족한 상품 생성 (예약된 재고가 실제 재고보다 많은 상황)
                Product outOfStockProduct = productRepositoryPort.save(Product.builder()
                        .name("재고 부족 상품")
                        .price(new BigDecimal("50000"))
                        .stock(0)
                        .reservedStock(1)  // 예약된 재고가 있지만 실제 재고가 없음
                        .build());

                // 재고 부족 상품으로 주문 생성
                OrderItem outOfStockOrderItem = OrderItem.builder().product(outOfStockProduct).quantity(1).price(outOfStockProduct.getPrice()).build();
                Order outOfStockOrder = orderRepositoryPort.save(Order.builder()
                        .user(testUser)
                        .totalAmount(new BigDecimal("50000"))
                        .items(List.of(outOfStockOrderItem))
                        .status(OrderStatus.PENDING)
                        .build());

                long orderId = outOfStockOrder.getId();
                OrderRequest request = new OrderRequest(testUser.getId(), null);

                // when & then
                mockMvc.perform(post("/api/order/{orderId}/pay", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isConflict()) // ProductException.OutOfStock는 409 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.PRODUCT_OUT_OF_STOCK.getMessage()));
            }
        }
    }
}
