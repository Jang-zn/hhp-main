package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 완료 이벤트 기반 상품 랭킹 업데이트 통합 테스트
 * 
 * Why: 실제 주문 결제 완료 시 이벤트를 통한 인기상품 랭킹 실시간 업데이트 검증
 * How: 주문 → 결제 → 이벤트 발행 → 랭킹 업데이트 전체 플로우 테스트
 * 
 * TODO: 실제 API 구조에 맞게 수정 필요
 */
@DisplayName("주문 완료 이벤트 기반 상품 랭킹 통합 테스트")
public class ProductRankingEventIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private ProductRepositoryPort productRepositoryPort;

    @Autowired
    private CachePort cachePort;

    @Autowired
    private KeyGenerator keyGenerator;

    @Test
    @DisplayName("주문 결제 완료 시 해당 상품들의 랭킹 점수가 비동기로 업데이트된다")
    void orderPayment_UpdatesProductRankingAsynchronously() throws Exception {
        // Given - 테스트 데이터 준비
        User customer = createUniqueTestUser();
        List<Product> products = createUniqueTestProducts();
        
        // 고객 잔액 충전 (API 호출) - 최대 1,000,000원 제한
        BalanceRequest chargeRequest = new BalanceRequest();
        chargeRequest.setUserId(customer.getId());
        chargeRequest.setAmount(BigDecimal.valueOf(1000000));
        
        mockMvc.perform(post("/api/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargeRequest)))
                .andExpect(status().isOk());
        
        // 주문 생성 (2개 상품 주문) (API 호출)
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setUserId(customer.getId());
        orderRequest.setProducts(List.of(
            new OrderRequest.ProductQuantity(products.get(0).getId(), 1), // 노트북 1개
            new OrderRequest.ProductQuantity(products.get(1).getId(), 1)  // 스마트폰 1개
        ));
        
        MvcResult orderResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        // 주문 ID 추출
        String orderResponseJson = orderResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode orderNode = objectMapper.readTree(orderResponseJson);
        Long orderId = orderNode.get("data").get("id").asLong();

        // Redis 랭킹 키 준비
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);

        // When - 주문 결제 실행 (API 호출)
        OrderRequest paymentRequest = new OrderRequest();
        paymentRequest.setUserId(customer.getId());
        
        mockMvc.perform(post("/api/order/" + orderId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andDo(print())
                .andExpect(status().isOk());

        // Then - 비동기 이벤트 처리를 기다린 후 랭킹 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Long> updatedRanking = cachePort.getProductRanking(dailyRankingKey, 0, 10);
            
            // 랭킹이 업데이트되었는지 확인
            assertThat(updatedRanking).isNotEmpty();
            assertThat(updatedRanking).contains(products.get(0).getId(), products.get(1).getId());
        });
        
    }

    @Test
    @DisplayName("여러 고객의 동일 상품 주문 시 랭킹 점수가 누적된다")
    void multipleCustomerOrders_AccumulateProductRankingScores() throws Exception {
        // Given - 테스트 데이터 준비
        User customer1 = createUniqueTestUser("고객1");
        User customer2 = createUniqueTestUser("고객2");
        List<Product> products = createUniqueTestProducts();
        
        Product popularProduct = products.get(0); // 노트북을 인기 상품으로 설정
        
        // 첫 번째 고객 주문 (노트북 2개) - API 호출
        createAndPayOrderViaAPI(customer1, popularProduct, 2);
        
        // 두 번째 고객 주문 (노트북 3개) - API 호출 
        createAndPayOrderViaAPI(customer2, popularProduct, 3);
        
        // Redis 랭킹 키 준비
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);

        // Then - 비동기 이벤트 처리를 기다린 후 누적 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Long> ranking = cachePort.getProductRanking(dailyRankingKey, 0, 1);
            
            // 인기 상품이 랭킹 1위에 있는지 확인 (총 5개 주문으로 높은 점수)
            assertThat(ranking).isNotEmpty();
            assertThat(ranking.get(0)).isEqualTo(popularProduct.getId());
        });
        
    }
    
    @Test
    @DisplayName("서로 다른 상품 주문 시 각각의 랭킹이 독립적으로 업데이트된다")
    void differentProductOrders_IndependentRankingUpdates() throws Exception {
        // Given - 테스트 데이터 준비
        User customer = createUniqueTestUser();
        List<Product> products = createUniqueTestProducts();
        
        // 서로 다른 상품들을 각각 주문 - API 호출
        createAndPayOrderViaAPI(customer, products.get(0), 5); // 노트북 5개
        createAndPayOrderViaAPI(customer, products.get(1), 3); // 스마트폰 3개
        createAndPayOrderViaAPI(customer, products.get(2), 1); // 태블릿 1개
        
        // Redis 랭킹 키 준비
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);

        // Then - 비동기 이벤트 처리를 기다린 후 랭킹 순서 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Long> ranking = cachePort.getProductRanking(dailyRankingKey, 0, 3);
            
            // 모든 상품이 랭킹에 포함되었는지 확인
            assertThat(ranking).hasSize(3);
            assertThat(ranking).containsAll(List.of(
                products.get(0).getId(), // 노트북
                products.get(1).getId(), // 스마트폰  
                products.get(2).getId()  // 태블릿
            ));
            
            // 주문 수량에 따른 순서 확인 (노트북 > 스마트폰 > 태블릿)
            assertThat(ranking.get(0)).isEqualTo(products.get(0).getId()); // 노트북 (5개)
        });
        
    }

    // === 헬퍼 메서드 ===
    
    private User createUniqueTestUser() {
        return createUniqueTestUser("테스트고객");
    }
    
    private User createUniqueTestUser(String baseName) {
        return userRepositoryPort.save(
            TestBuilder.UserBuilder.defaultUser()
                .name(baseName + "_" + System.nanoTime())
                .build()
        );
    }
    
    private List<Product> createUniqueTestProducts() {
        String suffix = "_" + System.nanoTime();
        
        Product notebook = productRepositoryPort.save(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("노트북" + suffix)
                .price(BigDecimal.valueOf(300000))
                .stock(100)
                .build()
        );
        
        Product smartphone = productRepositoryPort.save(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("스마트폰" + suffix)
                .price(BigDecimal.valueOf(200000))
                .stock(100)
                .build()
        );
        
        Product tablet = productRepositoryPort.save(
            TestBuilder.ProductBuilder.defaultProduct()
                .name("태블릿" + suffix)
                .price(BigDecimal.valueOf(100000))
                .stock(100)
                .build()
        );
        
        return List.of(notebook, smartphone, tablet);
    }
    
    
    private Long createAndPayOrderViaAPI(User customer, Product product, int quantity) throws Exception {
        // 잔액 충전
        BalanceRequest chargeRequest = new BalanceRequest();
        chargeRequest.setUserId(customer.getId());
        chargeRequest.setAmount(BigDecimal.valueOf(1000000)); // 최대 충전 금액으로 설정
        
        mockMvc.perform(post("/api/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargeRequest)))
                .andExpect(status().isOk());
        
        // 주문 생성
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setUserId(customer.getId());
        orderRequest.setProducts(List.of(
            new OrderRequest.ProductQuantity(product.getId(), quantity)
        ));
        
        MvcResult orderResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        // 주문 ID 추출
        String orderResponseJson = orderResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode orderNode = objectMapper.readTree(orderResponseJson);
        Long orderId = orderNode.get("data").get("id").asLong();
        
        // 주문 결제
        OrderRequest paymentRequest = new OrderRequest();
        paymentRequest.setUserId(customer.getId());
        
        mockMvc.perform(post("/api/order/" + orderId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());
                
        return orderId;
    }
}