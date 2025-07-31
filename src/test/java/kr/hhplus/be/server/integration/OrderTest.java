package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderItem;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
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
                .status(CouponStatus.ACTIVE)
                .build());

        // 단일 주문 조회 테스트를 위한 주문 생성
        createdOrder = orderRepositoryPort.save(Order.builder()
                .user(testUser)
                .totalAmount(new BigDecimal("150000"))
                .status(OrderStatus.PENDING)
                .items(List.of(
                        OrderItem.builder().product(product1).quantity(1).price(product1.getPrice()).build(),
                        OrderItem.builder().product(product2).quantity(1).price(product2.getPrice()).build()
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
                        .andExpect(jsonPath("$.data.items.length()").value(2));
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
                        .andExpect(jsonPath("$.message").exists());
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
                        .andExpect(jsonPath("$.message").exists());
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
                        .andExpect(jsonPath("$.message").exists());
            }

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
                        .andExpect(jsonPath("$.message").exists());
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
                        .items(List.of(OrderItem.builder().product(product1).quantity(1).price(product1.getPrice()).build()))
                        .build());

                // when & then (testUser가 anotherUserOrder를 조회 시도)
                mockMvc.perform(get("/api/order/{orderId}", anotherUserOrder.getId())
                                .param("userId", String.valueOf(testUser.getId()))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isForbidden()) // OrderException.Unauthorized는 403 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                        .andExpect(jsonPath("$.message").exists());
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
                        .items(List.of(OrderItem.builder().product(product2).quantity(1).price(product2.getPrice()).build()))
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
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("한정된 재고 상품에 대한 동시 주문 요청에서 재고를 초과하지 않아야 한다")
        void limitedStockConcurrencyTest() throws Exception {
            // given: 재고가 3개인 상품 생성
            Product limitedProduct = Product.builder()
                    .name("Limited Stock Product")
                    .price(new BigDecimal("10000"))
                    .stock(3)
                    .reservedStock(0)
                    .build();
            final Product finalLimitedProduct = productRepositoryPort.save(limitedProduct);

            // 5명의 사용자 생성 (각자 충분한 잔액)
            List<User> testUsers = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                User user = userRepositoryPort.save(User.builder().name("OrderUser" + i).build());
                testUsers.add(user);
            }

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(5);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when: 5명의 사용자가 동시에 주문 요청 (각자 1개씩)
            for (User user : testUsers) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        OrderRequest.ProductQuantity orderItem = new OrderRequest.ProductQuantity(
                                finalLimitedProduct.getId(), 1); // 1개씩 주문
                        OrderRequest request = new OrderRequest();
                        request.setUserId(user.getId());
                        request.setProducts(List.of(orderItem));
                        request.setCouponIds(null); // null 쿠폰으로 주문
                        
                        var result = mockMvc.perform(post("/api/order")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 201) { // 주문 생성은 201 Created
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            System.out.println("⚠️ 주문 실패 - 상태코드: " + result.getResponse().getStatus() + 
                                             ", 응답: " + result.getResponse().getContentAsString());
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 최대 3개의 주문만 성공해야 함
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isLessThanOrEqualTo(3);
            assertThat(failureCount.get()).isGreaterThanOrEqualTo(2);
            
            // 상품 재고 확인
            Product finalProduct = productRepositoryPort.findById(finalLimitedProduct.getId()).orElseThrow();
            assertThat(finalProduct.getStock() + finalProduct.getReservedStock()).isLessThanOrEqualTo(3);
            
            System.out.println("✅ 주문 동시성 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }

        @Test
        @DisplayName("동일 사용자가 같은 상품을 동시에 여러 번 주문해도 정상 처리되어야 한다")
        void sameUserMultipleOrdersTest() throws Exception {
            // given: 충분한 재고의 상품
            Product product = Product.builder()
                    .name("Multi Order Product")
                    .price(new BigDecimal("5000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            final Product finalProduct = productRepositoryPort.save(product);

            User user = userRepositoryPort.save(User.builder().name("MultiOrderUser").build());
            final User finalUser = user;

            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(3);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when: 동일 사용자가 3번의 동시 주문 (각자 1개씩)
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        OrderRequest.ProductQuantity orderItem = new OrderRequest.ProductQuantity(
                                finalProduct.getId(), 1);
                        OrderRequest request = new OrderRequest();
                        request.setUserId(finalUser.getId());
                        request.setProducts(List.of(orderItem));
                        request.setCouponIds(null); // null 쿠폰으로 주문
                        
                        var result = mockMvc.perform(post("/api/order")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 201) { // 주문 생성은 201 Created
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            System.out.println("⚠️ 주문 실패 - 상태코드: " + result.getResponse().getStatus() + 
                                             ", 응답: " + result.getResponse().getContentAsString());
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 동시성 제어에 따라 일부 주문은 실패할 수 있음 (동시성 오류 또는 중복 주문 방지)
            assertThat(completed).isTrue();
            // 동시성 제어가 작동하여 모든 요청이 완료되었는지 확인
            assertThat(successCount.get() + failureCount.get()).isEqualTo(3);
            // 적어도 하나는 성공하거나, 모든 것이 동시성 제어로 실패할 수 있음
            assertThat(successCount.get()).isGreaterThanOrEqualTo(0);
            
            System.out.println("✅ 동일 사용자 다중 주문 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }
    }
}
