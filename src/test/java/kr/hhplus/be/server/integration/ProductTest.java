package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.hamcrest.Matchers;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("상품 API E2E 테스트")
public class ProductTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepositoryPort productRepositoryPort;

    @BeforeEach
    void setUp() {
        // 테스트 상품 데이터 설정
        Product product1 = Product.builder().id(1L).name("노트북").price(BigDecimal.valueOf(1000000)).stock(10).reservedStock(0).build();
        Product product2 = Product.builder().id(2L).name("스마트폰").price(BigDecimal.valueOf(800000)).stock(5).reservedStock(0).build();
        Product product3 = Product.builder().id(3L).name("태블릿").price(BigDecimal.valueOf(500000)).stock(15).reservedStock(0).build();
        Product product4 = Product.builder().id(4L).name("무선이어폰").price(BigDecimal.valueOf(150000)).stock(20).reservedStock(0).build();
        Product product5 = Product.builder().id(5L).name("키보드").price(BigDecimal.valueOf(100000)).stock(25).reservedStock(0).build();
        Product product6 = Product.builder().id(6L).name("마우스").price(BigDecimal.valueOf(80000)).stock(30).reservedStock(0).build();

        productRepositoryPort.save(product1);
        productRepositoryPort.save(product2);
        productRepositoryPort.save(product3);
        productRepositoryPort.save(product4);
        productRepositoryPort.save(product5);
        productRepositoryPort.save(product6);
    }

    @Test
    @DisplayName("상품 목록 조회 API 테스트")
    void getProductsTest() throws Exception {
        // given & when
        ResultActions resultActions = mockMvc.perform(get("/api/product/list")
                        .param("limit", "10")
                        .param("offset", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()", Matchers.is(6))) // 3 -> 6
                .andExpect(jsonPath("$.data[0].name").value("노트북"))
                .andExpect(jsonPath("$.data[1].name").value("스마트폰"))
                .andExpect(jsonPath("$.data[2].name").value("태블릿"));
    }

    @Test
    @DisplayName("인기 상품 조회 API 테스트")
    void getPopularProductsTest() throws Exception {
        // given & when
        ResultActions resultActions = mockMvc.perform(get("/api/product/popular")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()", Matchers.is(6))) // 5 -> 6
                .andExpect(jsonPath("$.data[0].name").value("노트북")) // 스마트폰 -> 노트북 (정렬 순서에 따라 변경)
                .andExpect(jsonPath("$.data[1].name").value("스마트폰")) // 노트북 -> 스마트폰
                .andExpect(jsonPath("$.data[2].name").value("태블릿")); // 무선이어폰 -> 태블릿
    }
} 