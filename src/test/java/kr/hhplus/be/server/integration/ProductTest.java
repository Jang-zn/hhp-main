package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.api.controller.ProductController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.hamcrest.Matchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc
@DisplayName("상품 API E2E 테스트")
public class ProductTest {
    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$.data.length()", Matchers.is(3)))
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
                .andExpect(jsonPath("$.data.length()", Matchers.is(5)))
                .andExpect(jsonPath("$.data[0].name").value("스마트폰"))
                .andExpect(jsonPath("$.data[1].name").value("노트북"))
                .andExpect(jsonPath("$.data[2].name").value("무선이어폰"));
    }
} 