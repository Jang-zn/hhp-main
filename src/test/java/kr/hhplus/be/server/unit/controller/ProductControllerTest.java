package kr.hhplus.be.server.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.controller.ProductController;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.facade.product.GetProductListFacade;
import kr.hhplus.be.server.domain.facade.product.GetPopularProductListFacade;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductController 비즈니스 시나리오 및 Bean Validation 테스트
 * 
 * Why: 상품 컨트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@WebMvcTest(ProductController.class)
@DisplayName("상품 컨트롤러 API 및 Validation 테스트")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetProductListFacade getProductListFacade;

    @MockBean
    private GetPopularProductListFacade getPopularProductListFacade;

    @Test
    @DisplayName("고객이 상품 목록을 성공적으로 조회한다")
    void getProductList_Success() throws Exception {
        // given - 고객이 상품 목록을 페이지별로 조회하는 상황
        int limit = 10;
        int offset = 0;

        List<Product> products = List.of(
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(1L)
                        .name("상품1")
                        .price(new BigDecimal("10000"))
                        .stock(100)
                        .build(),
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(2L)
                        .name("상품2")
                        .price(new BigDecimal("20000"))
                        .stock(50)
                        .build()
        );

        when(getProductListFacade.getProductList(limit, offset)).thenReturn(products);

        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("상품1"))
                .andExpect(jsonPath("$.data[1].name").value("상품2"));
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                // limit이 음수
                Arguments.of(-1, 0, "limit이 음수"),
                // limit이 0
                Arguments.of(0, 0, "limit이 0"),
                // limit이 너무 큼 (100 초과)
                Arguments.of(101, 0, "limit이 100 초과"),
                // offset이 음수
                Arguments.of(10, -1, "offset이 음수")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("유효하지 않은 페이지네이션 파라미터로 상품 목록 조회 시 validation 에러가 발생한다")
    void getProductList_InvalidPagination_ValidationError(int limit, int offset, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("고객이 인기 상품 목록을 성공적으로 조회한다")
    void getPopularProductList_Success() throws Exception {
        // given - 고객이 인기 상품 목록을 조회하는 상황
        int limit = 5;

        List<Product> popularProducts = List.of(
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(1L)
                        .name("인기상품1")
                        .price(new BigDecimal("15000"))
                        .stock(200)
                        .build()
        );

        when(getPopularProductListFacade.getPopularProductList(limit))
                .thenReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/api/product/popular")
                .param("days", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("인기상품1"));
    }

    static Stream<Arguments> provideInvalidPopularProductLimits() {
        return Stream.of(
                Arguments.of(-1, "limit이 음수"),
                Arguments.of(0, "limit이 0"),
                Arguments.of(101, "limit이 100 초과")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPopularProductLimits")
    @DisplayName("유효하지 않은 limit으로 인기 상품 조회 시 validation 에러가 발생한다")
    void getPopularProductList_InvalidLimit_ValidationError(int limit, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/product/popular")
                .param("days", String.valueOf(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 페이지네이션 파라미터로 상품 조회 시 기본값이 적용된다")
    void getProductList_EmptyParams_DefaultValues() throws Exception {
        // given - 파라미터 없이 조회하는 상황 (기본값 적용)
        List<Product> products = List.of(
                TestBuilder.ProductBuilder.defaultProduct()
                        .id(1L)
                        .name("기본상품")
                        .build()
        );

        when(getProductListFacade.getProductList(anyInt(), anyInt())).thenReturn(products);

        // when & then - Use no parameters for default values
        mockMvc.perform(get("/api/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("서비스 에러 발생 시 적절한 에러 응답을 반환한다")
    void getProductList_ServiceError() throws Exception {
        // given - 서비스에서 에러가 발생하는 상황
        when(getProductListFacade.getProductList(anyInt(), anyInt()))
                .thenThrow(new CommonException.InvalidRequest());

        // when & then
        mockMvc.perform(get("/api/product/list")
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }
}