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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductController.getPopularProductList 메서드 테스트
 */
@WebMvcTest(ProductController.class)
@DisplayName("인기 상품 목록 조회 컨트롤러 API")
class GetPopularProductListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @DisplayName("고객이 인기 상품 목록을 성공적으로 조회한다")
    void getPopularProductList_Success() throws Exception {
        // given
        int days = 7;

        List<Product> popularProducts = List.of(
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(1L)
                        .name("인기 상품 1")
                        .price(new BigDecimal("45000"))
                        .build(),
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(2L)
                        .name("인기 상품 2")
                        .price(new BigDecimal("55000"))
                        .build()
        );

        when(productService.getPopularProductList(eq(days), anyInt(), anyInt())).thenReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/api/product/popular")
                .param("days", String.valueOf(days)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("인기 상품 1"))
                .andExpect(jsonPath("$.data[0].price").value(45000))
                .andExpect(jsonPath("$.data[1].productId").value(2L))
                .andExpect(jsonPath("$.data[1].name").value("인기 상품 2"))
                .andExpect(jsonPath("$.data[1].price").value(55000));
    }

    @Test
    @DisplayName("빈 인기 상품 목록 조회가 성공한다")
    void getPopularProductList_EmptyList() throws Exception {
        // given
        int days = 7;

        when(productService.getPopularProductList(eq(days), anyInt(), anyInt())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/product/popular")
                .param("days", String.valueOf(days)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    static Stream<Arguments> provideInvalidDaysParams() {
        return Stream.of(
                Arguments.of(-1, "음수 days"),
                Arguments.of(0, "0인 days"),
                Arguments.of(31, "최대값 초과 days")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidDaysParams")
    @DisplayName("유효하지 않은 days 파라미터로 조회 시 validation 에러가 발생한다")
    void getPopularProductList_InvalidDaysParams_ValidationError(int days, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/product/popular")
                .param("days", String.valueOf(days)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("기본값으로 인기 상품 목록 조회가 성공한다")
    void getPopularProductList_DefaultParams() throws Exception {
        // given
        List<Product> popularProducts = List.of(
                TestBuilder.ProductBuilder.defaultProduct().id(1L).name("인기 상품 1").build()
        );

        when(productService.getPopularProductList(anyInt(), anyInt(), anyInt())).thenReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/api/product/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}