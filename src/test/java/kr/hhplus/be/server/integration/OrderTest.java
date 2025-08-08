package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.OrderStatus;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.OrderRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import kr.hhplus.be.server.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 API 통합 테스트
 * 
 * Why: 주문 생성부터 조회까지의 전체 플로우가 비즈니스 요구사항을 만족하는지 검증
 * How: 실제 고객의 주문 시나리오를 반영한 API 레벨 테스트
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("주문 API 통합 시나리오")
public class OrderTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepositoryPort userRepositoryPort;
    @Autowired private ProductRepositoryPort productRepositoryPort;
    @Autowired private CouponRepositoryPort couponRepositoryPort;
    @Autowired private OrderRepositoryPort orderRepositoryPort;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 개별 데이터를 생성하여 OptimisticLocking 충돌 회피
    }

    @Test
    @DisplayName("고객이 복수 상품과 쿠폰으로 주문을 생성할 수 있다")
    void customerCanCreateOrderWithMultipleProductsAndCoupon() throws Exception {
        // Given
        User customer = createUniqueCustomer("다상품고객");
        Product notebook = createUniqueProduct("노트북A", "100000", 10);
        Product mouse = createUniqueProduct("마우스A", "50000", 5);
        Coupon coupon = createUniqueCoupon("MULTI_ORDER_COUPON");
        
        OrderRequest request = createOrderRequest(customer.getId(), 
            List.of(
                new OrderRequest.ProductQuantity(notebook.getId(), 2),
                new OrderRequest.ProductQuantity(mouse.getId(), 1)
            ),
            List.of(coupon.getId())
        );

        // When & Then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.userId").value(customer.getId()))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("쿠폰 없이도 주문을 생성할 수 있다")
    void customerCanCreateOrderWithoutCoupon() throws Exception {
        // Given
        User customer = createUniqueCustomer("빠뜨고객");
        Product product = createUniqueProduct("노트북B", "100000", 10);
        
        OrderRequest request = createOrderRequest(customer.getId(), 
            List.of(new OrderRequest.ProductQuantity(product.getId(), 1)), 
            null
        );

        // When & Then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 주문 요청은 차단된다")
    void preventsOrderFromNonExistentCustomer() throws Exception {
        // Given
        Product product = createUniqueProduct("노트북C", "100000", 10);
        
        OrderRequest request = createOrderRequest(999L, 
            List.of(new OrderRequest.ProductQuantity(product.getId(), 1)), 
            null
        );

        // When & Then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 상품의 주문 요청은 차단된다")
    void preventsOrderForNonExistentProduct() throws Exception {
        // Given
        User customer = createUniqueCustomer("비상품고객");
        
        OrderRequest request = createOrderRequest(customer.getId(), 
            List.of(new OrderRequest.ProductQuantity(999L, 1)), 
            null
        );

        // When & Then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("재고 부족 상품의 주문 요청은 차단된다")
    void preventsOrderWhenProductOutOfStock() throws Exception {
        // Given
        User customer = createUniqueCustomer("재고고객");
        Product product = createUniqueProduct("노트북D", "100000", 5);
        
        OrderRequest request = createOrderRequest(customer.getId(), 
            List.of(new OrderRequest.ProductQuantity(product.getId(), 100)), 
            null
        );

        // When & Then
        mockMvc.perform(post("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode()));
    }

    @Test
    @DisplayName("고객이 자신의 주문을 조회할 수 있다")
    void customerCanViewTheirOrder() throws Exception {
        // Given
        User customer = createUniqueCustomer("조회고객");
        Order existingOrder = createUniqueOrder(customer, new BigDecimal("150000"));
        
        // When & Then
        mockMvc.perform(get("/api/order/{orderId}", existingOrder.getId())
                .param("userId", String.valueOf(customer.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data.orderId").value(existingOrder.getId()))
            .andExpect(jsonPath("$.data.userId").value(customer.getId()));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회는 실패한다")
    void failsWhenViewingNonExistentOrder() throws Exception {
        // Given
        User customer = createUniqueCustomer("미존재주문고객");
        
        // When & Then
        mockMvc.perform(get("/api/order/{orderId}", 999L)
                .param("userId", String.valueOf(customer.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("다른 고객의 주문 조회는 차단된다")
    void preventsViewingOtherCustomersOrder() throws Exception {
        // Given
        User customer1 = createUniqueCustomer("고객1");
        User customer2 = createUniqueCustomer("고객2");
        Order anotherOrder = createUniqueOrder(customer2, new BigDecimal("100000"));

        // When & Then
        mockMvc.perform(get("/api/order/{orderId}", anotherOrder.getId())
                .param("userId", String.valueOf(customer1.getId())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("고객이 자신의 주문 목록을 조회할 수 있다")
    void customerCanViewTheirOrderHistory() throws Exception {
        // Given
        User customer = createUniqueCustomer("목록고객");
        createUniqueOrder(customer, new BigDecimal("100000"));
        createUniqueOrder(customer, new BigDecimal("200000"));

        // When & Then
        mockMvc.perform(get("/api/order/user/{userId}", customer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("주문이 없는 고객은 빈 목록을 받는다")
    void customerWithNoOrdersGetsEmptyList() throws Exception {
        // Given
        User newCustomer = createUniqueCustomer("신규고객");

        // When & Then
        mockMvc.perform(get("/api/order/user/{userId}", newCustomer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 고객의 주문 목록 조회는 실패한다")
    void failsWhenViewingNonExistentCustomerOrders() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/order/user/{userId}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("한정 수량 상품에 대한 동시 주문에서 재고가 음수가 되지 않는다")
    void preventsNegativeStockUnderConcurrentLimitedItemOrders() throws Exception {
        // Given - 재고 3개 한정 상품과 5명의 고객
        Product limitedProduct = createUniqueProduct("한정상품", "10000", 3);
        List<User> customers = createMultipleCustomers(5);

        // When - 5명이 동시에 주문
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                try {
                    User customer = customers.get((int)(Math.random() * customers.size()));
                    OrderRequest request = createOrderRequest(customer.getId(), 
                        List.of(new OrderRequest.ProductQuantity(limitedProduct.getId(), 1)), 
                        null
                    );

                    var response = mockMvc.perform(post("/api/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();

                    return response.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });

        // Then - 최대 3개 주문만 성공하고 재고가 음수가 되지 않음
        assertThat(result.getTotalCount()).isEqualTo(5);
        
        Product finalProduct = productRepositoryPort.findById(limitedProduct.getId()).orElseThrow();
        assertThat(finalProduct.getStock() + finalProduct.getReservedStock()).isLessThanOrEqualTo(3);
        assertThat(finalProduct.getStock()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("동일 고객의 여러 동시 주문도 안정적으로 처리된다")
    void stableProcessingForSameCustomerConcurrentOrders() throws Exception {
        // Given - 충분한 재고의 상품과 잔액이 충분한 고객
        User customer = createUniqueCustomer("동시주문고객");
        Product product = createUniqueProduct("일반상품", "5000", 10);
        
        // 고객 잔액 충전
        BalanceRequest balanceRequest = new BalanceRequest();
        balanceRequest.setAmount(new BigDecimal("20000"));
        mockMvc.perform(post("/api/balance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(balanceRequest)))
            .andExpect(status().isOk());

        // 초기 상태 저장
        Product initialProduct = productRepositoryPort.findById(product.getId()).orElseThrow();
        int initialStock = initialProduct.getStock();
        int initialReservedStock = initialProduct.getReservedStock();
        
        User initialCustomer = userRepositoryPort.findById(customer.getId()).orElseThrow();
        BigDecimal initialBalance = initialCustomer.getBalance();

        // When - 동일 고객의 3번 동시 주문
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                try {
                    OrderRequest request = createOrderRequest(customer.getId(), 
                        List.of(new OrderRequest.ProductQuantity(product.getId(), 1)), 
                        null
                    );

                    var response = mockMvc.perform(post("/api/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn();

                    return response.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });

        // Then - 동시성 제어가 안정적으로 동작
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        // 비즈니스 상태 검증: 재고가 올바르게 차감되었는지 확인
        Product finalProduct = productRepositoryPort.findById(product.getId()).orElseThrow();
        assertThat(finalProduct.getStock())
            .as("재고가 올바르게 차감되어야 함")
            .isEqualTo(initialStock - 3);
        assertThat(finalProduct.getReservedStock())
            .as("예약 재고는 0이어야 함 (주문 완료 후)")
            .isEqualTo(0);
        
        // 비즈니스 상태 검증: 고객 잔액이 올바르게 차감되었는지 확인
        User finalCustomer = userRepositoryPort.findById(customer.getId()).orElseThrow();
        BigDecimal expectedDeduction = new BigDecimal("5000").multiply(new BigDecimal("3")); // 상품가격 * 주문수량
        assertThat(finalCustomer.getBalance())
            .as("고객 잔액이 올바르게 차감되어야 함")
            .isEqualTo(initialBalance.subtract(expectedDeduction));
        
        // 주문 레코드 검증: 3개의 주문이 생성되었는지 확인
        List<Order> customerOrders = orderRepositoryPort.findByUserId(customer.getId());
        assertThat(customerOrders)
            .as("3개의 주문이 생성되어야 함")
            .hasSize(3);
        
        // 각 주문의 총액이 올바른지 확인
        customerOrders.forEach(order -> {
            assertThat(order.getTotalAmount())
                .as("각 주문의 총액이 상품 가격과 일치해야 함")
                .isEqualTo(new BigDecimal("5000"));
        });
    }

    // === 헬퍼 메서드 ===
    
    private OrderRequest createOrderRequest(Long userId, List<OrderRequest.ProductQuantity> products, List<Long> couponIds) {
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setProducts(products);
        request.setCouponIds(couponIds);
        return request;
    }
    
    private List<User> createMultipleCustomers(int count) {
        List<User> customers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            customers.add(userRepositoryPort.save(
                TestBuilder.UserBuilder.defaultUser().name("고객" + i + "_" + System.nanoTime()).build()
            ));
        }
        return customers;
    }
    
    private User createUniqueCustomer(String name) {
        return userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser()
                .name(name + "_" + System.nanoTime())
                .build()
        );
    }
    
    private Product createUniqueProduct(String name, String price, int stock) {
        return productRepositoryPort.save(
            TestBuilder.ProductBuilder.defaultProduct()
                .name(name + "_" + System.nanoTime())
                .price(new BigDecimal(price))
                .stock(stock)
                .build()
        );
    }
    
    private Coupon createUniqueCoupon(String code) {
        return couponRepositoryPort.save(
            TestBuilder.CouponBuilder.defaultCoupon()
                .code(code + "_" + System.nanoTime())
                .build()
        );
    }
    
    private Order createUniqueOrder(User user, BigDecimal amount) {
        return orderRepositoryPort.save(
            TestBuilder.OrderBuilder.defaultOrder()
                .userId(user.getId())
                .totalAmount(amount)
                .build()
        );
    }
}