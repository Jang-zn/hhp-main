package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("주문 API 통합 테스트")
public class OrderTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;
    @Autowired
    private ProductRepositoryPort productRepositoryPort;
    @Autowired
    private CouponRepositoryPort couponRepositoryPort;
    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    private User testUser;
    private Product product1;
    private Product product2;
    private Coupon availableCoupon;
    private Order createdOrder;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 설정
        testUser = userRepositoryPort.save(User.builder().name("Test User").build());

        // 테스트 상품 설정
        product1 = productRepositoryPort.save(Product.builder().name("노트북").price(new BigDecimal("100000")).stock(10).reservedStock(0).build());
        product2 = productRepositoryPort.save(Product.builder().name("마우스").price(new BigDecimal("50000")).stock(5).reservedStock(0).build());

        // 테스트 쿠폰 설정
        availableCoupon = couponRepositoryPort.save(Coupon.builder()
                .code("ORDER_COUPON")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build());

        // 단일 주문 조회 테스트를 위한 주문 생성
        createdOrder = orderRepositoryPort.save(Order.builder()
                .user(testUser)
                .totalAmount(new BigDecimal("150000"))
                .status(OrderStatus.PENDING)
                .items(List.of(
                        OrderItem.builder().product(product1).quantity(1).build(),
                        OrderItem.builder().product(product2).quantity(1).build()
                ))
                .build());
    }

    @Nested
    @DisplayName("POST /api/order - 주문 생성")
    class CreateOrder {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("정상적인 상품 목록으로 주문 생성 요청 시 201 Created와 함께 주문 정보를 반환한다")
            void createOrder_Success() throws Exception {
                // given
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(product1.getId(), 2),
                        new OrderRequest.ProductQuantity(product2.getId(), 1)
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(products);
                request.setCouponIds(List.of(availableCoupon.getId()));

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.userId").value(testUser.getId()))
                        .andExpect(jsonPath("$.data.status").value("PENDING"))
                        .andExpect(jsonPath("$.data.items[0].name").value(product1.getName()));
            }

            @Test
            @DisplayName("쿠폰 없이 주문 생성 요청 시 201 Created와 함께 주문 정보를 반환한다")
            void createOrder_NoCoupon_Success() throws Exception {
                // given
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(product1.getId(), 1)
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(products);
                request.setCouponIds(null);

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.userId").value(testUser.getId()))
                        .andExpect(jsonPath("$.data.status").value("PENDING"));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 사용자 ID로 주문 생성 요청 시 404 Not Found를 반환한다")
            void createOrder_UserNotFound_ShouldFail() throws Exception {
                // given
                long nonExistentUserId = 999L;
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(product1.getId(), 1)
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(nonExistentUserId);
                request.setProducts(products);
                request.setCouponIds(null);

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // UserException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(UserException.Messages.USER_NOT_FOUND));
            }

            @Test
            @DisplayName("존재하지 않는 상품 ID로 주문 생성 요청 시 404 Not Found를 반환한다")
            void createOrder_ProductNotFound_ShouldFail() throws Exception {
                // given
                long nonExistentProductId = 999L;
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(nonExistentProductId, 1)
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(products);
                request.setCouponIds(null);

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // ProductException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(ProductException.Messages.PRODUCT_NOT_FOUND));
            }

            @Test
            @DisplayName("상품 재고가 부족할 경우 주문 생성 요청 시 409 Conflict를 반환한다")
            void createOrder_OutOfStock_ShouldFail() throws Exception {
                // given
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(product1.getId(), 100) // 재고보다 많은 수량
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(products);
                request.setCouponIds(null);

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isConflict()) // ProductException.OutOfStock는 409 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode()))
                        .andExpect(jsonPath("$.message").value(ProductException.Messages.OUT_OF_STOCK));
            }

            @Test
            @DisplayName("유효하지 않은 쿠폰 ID로 주문 생성 요청 시 404 Not Found를 반환한다")
            void createOrder_InvalidCoupon_ShouldFail() throws Exception {
                // given
                long invalidCouponId = 999L;
                List<OrderRequest.ProductQuantity> products = List.of(
                        new OrderRequest.ProductQuantity(product1.getId(), 1)
                );
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(products);
                request.setCouponIds(List.of(invalidCouponId));

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // CouponException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(CouponException.Messages.COUPON_NOT_FOUND));
            }

            @Test
            @DisplayName("상품 목록이 null일 경우 주문 생성 요청 시 400 Bad Request를 반환한다")
            void createOrder_NullProductList_ShouldFail() throws Exception {
                // given
                OrderRequest request = new OrderRequest();
                request.setUserId(testUser.getId());
                request.setProducts(null); // null 상품 목록
                request.setCouponIds(null);

                // when & then
                mockMvc.perform(post("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Nested
    @DisplayName("GET /api/order/{orderId} - 단일 주문 조회")
    class GetOrder {
        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("정상적인 주문 ID로 단일 주문 조회 요청 시 200 OK와 함께 주문 정보를 반환한다")
            void getOrder_Success() throws Exception {
                // given
                long orderId = createdOrder.getId();

                // when & then
                mockMvc.perform(get("/api/order/{orderId}", orderId)
                                .param("userId", String.valueOf(testUser.getId()))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.orderId").value(orderId))
                        .andExpect(jsonPath("$.data.userId").value(testUser.getId()));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 주문 ID로 단일 주문 조회 요청 시 404 Not Found를 반환한다")
            void getOrder_NotFound_ShouldFail() throws Exception {
                // given
                long nonExistentOrderId = 999L;

                // when & then
                mockMvc.perform(get("/api/order/{orderId}", nonExistentOrderId)
                                .param("userId", String.valueOf(testUser.getId()))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // OrderException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(OrderException.Messages.ORDER_NOT_FOUND));
            }

            @Test
            @DisplayName("다른 사용자의 주문 ID로 단일 주문 조회 요청 시 403 Forbidden을 반환한다")
            void getOrder_Unauthorized_ShouldFail() throws Exception {
                // given
                User anotherUser = userRepositoryPort.save(User.builder().name("Another User").build());
                Order anotherUserOrder = orderRepositoryPort.save(Order.builder()
                        .user(anotherUser)
                        .totalAmount(new BigDecimal("10000"))
                        .status(OrderStatus.PENDING)
                        .items(List.of(OrderItem.builder().product(product1).quantity(1).build()))
                        .build());

                // when & then (testUser가 anotherUserOrder를 조회 시도)
                mockMvc.perform(get("/api/order/{orderId}", anotherUserOrder.getId())
                                .param("userId", String.valueOf(testUser.getId()))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isForbidden()) // OrderException.Unauthorized는 403 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                        .andExpect(jsonPath("$.message").value(OrderException.Messages.UNAUTHORIZED_ACCESS));
            }
        }
    }

    @Nested
    @DisplayName("GET /api/order/user/{userId} - 사용자 주문 목록 조회")
    class GetUserOrders {
        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("정상적인 사용자 ID로 주문 목록 조회 요청 시 200 OK와 함께 주문 목록을 반환한다")
            void getUserOrders_Success() throws Exception {
                // given
                // 추가 주문 생성 (testUser의 주문)
                orderRepositoryPort.save(Order.builder()
                        .user(testUser)
                        .totalAmount(new BigDecimal("50000"))
                        .status(OrderStatus.PAID)
                        .items(List.of(OrderItem.builder().product(product2).quantity(1).build()))
                        .build());

                // when & then
                mockMvc.perform(get("/api/order/user/{userId}", testUser.getId())
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data.length()").value(2)); // createdOrder + 새로 생성된 주문
            }

            @Test
            @DisplayName("주문이 없는 사용자 ID로 주문 목록 조회 요청 시 빈 배열을 반환한다")
            void getUserOrders_NoOrders_Success() throws Exception {
                // given
                User userWithoutOrders = userRepositoryPort.save(User.builder().name("User Without Orders").build());

                // when & then
                mockMvc.perform(get("/api/order/user/{userId}", userWithoutOrders.getId())
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data.length()").value(0));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 사용자 ID로 주문 목록 조회 요청 시 404 Not Found를 반환한다")
            void getUserOrders_UserNotFound_ShouldFail() throws Exception {
                // given
                long nonExistentUserId = 999L;

                // when & then
                mockMvc.perform(get("/api/order/user/{userId}", nonExistentUserId)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // UserException.NotFound는 404 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(UserException.Messages.USER_NOT_FOUND));
            }
        }
    }
}
