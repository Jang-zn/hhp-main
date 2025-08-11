package kr.hhplus.be.server.unit.controller.product;

import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.service.ProductService;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductController.getProductList 메서드 테스트
 */
@WebMvcTest(ProductController.class)
@DisplayName("상품 목록 조회 컨트롤러 API")
class GetProductListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("고객이 상품 목록을 성공적으로 조회한다")
    void getProductList_Success() throws Exception {
        // given
        int limit = 10;
        int offset = 0;

        List<Product> products = List.of(
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(1L)
                        .name("상품 1")
                        .price(new BigDecimal("25000"))
                        .build(),
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(2L)
                        .name("상품 2")
                        .price(new BigDecimal("35000"))
                        .build()
        );

        when(productService.getProductList(limit, offset)).thenReturn(products);

        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("상품 1"))
                .andExpect(jsonPath("$.data[0].price").value(25000))
                .andExpect(jsonPath("$.data[1].productId").value(2L))
                .andExpect(jsonPath("$.data[1].name").value("상품 2"))
                .andExpect(jsonPath("$.data[1].price").value(35000));
    }

    @Test
    @DisplayName("빈 상품 목록 조회가 성공한다")
    void getProductList_EmptyList() throws Exception {
        // given
        int limit = 10;
        int offset = 0;

        when(productService.getProductList(limit, offset)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                Arguments.of(-1, 0, "음수 limit"),
                Arguments.of(0, 0, "0인 limit"),
                Arguments.of(101, 0, "최대값 초과 limit"),
                Arguments.of(10, -1, "음수 offset")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("유효하지 않은 페이징 파라미터로 조회 시 validation 에러가 발생한다")
    void getProductList_InvalidPaginationParams_ValidationError(int limit, int offset, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("기본값으로 상품 목록 조회가 성공한다")
    void getProductList_DefaultParams() throws Exception {
        // given
        List<Product> products = List.of(
                TestBuilder.ProductBuilder.defaultProduct().id(1L).name("상품 1").build()
        );

        when(productService.getProductList(anyInt(), anyInt())).thenReturn(products);

        // when & then
        mockMvc.perform(get("/api/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}