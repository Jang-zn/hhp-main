package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.OrderRequest;
import kr.hhplus.be.server.api.controller.OrderController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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