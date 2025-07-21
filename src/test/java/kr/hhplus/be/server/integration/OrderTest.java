package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.controller.OrderController;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("주문 API E2E 테스트")
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

    @BeforeEach
    void setUp() {
        // 테스트 사용자 설정
        User user = User.builder().id(1L).name("Test User").build();
        userRepositoryPort.save(user);

        // 테스트 상품 설정
        Product product1 = Product.builder().id(1L).name("노트북").price(new BigDecimal("100000")).stock(10).reservedStock(0).build();
        Product product2 = Product.builder().id(2L).name("마우스").price(new BigDecimal("50000")).stock(5).reservedStock(0).build();
        productRepositoryPort.save(product1);
        productRepositoryPort.save(product2);

        // 테스트 쿠폰 설정
        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("ORDER_COUPON")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        couponRepositoryPort.save(coupon);
    }

    @Test
    @DisplayName("주문 생성 API 테스트")
    void createOrderTest() throws Exception {
        // given
        long userId = 1L;
        List<OrderRequest.ProductQuantity> products = List.of(
            new OrderRequest.ProductQuantity(1L, 2),
            new OrderRequest.ProductQuantity(2L, 1)
        );
        List<Long> couponIds = List.of(1L);
        OrderRequest request = new OrderRequest(userId, null, couponIds);
        request.setProducts(products);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].name").value("노트북"));
    }

    @Test
    @DisplayName("기존 productIds 필드 사용 주문 생성 API 테스트 (하위 호환성)")
    void createOrderWithLegacyProductIdsTest() throws Exception {
        // given
        long userId = 1L;
        List<Long> productIds = List.of(1L, 2L);
        List<Long> couponIds = List.of(1L);
        OrderRequest request = new OrderRequest(userId, productIds, couponIds);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
} 