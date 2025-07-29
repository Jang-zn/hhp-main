package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("상품 API 통합 테스트")
public class ProductTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepositoryPort productRepositoryPort;
    

    @BeforeEach
    void setUp() {
        // 테스트 상품 데이터 설정 (정확히 6개)
        productRepositoryPort.save(Product.builder().name("노트북").price(new BigDecimal("1500000")).stock(10).reservedStock(0).build());
        productRepositoryPort.save(Product.builder().name("스마트폰").price(new BigDecimal("1200000")).stock(20).reservedStock(0).build());
        productRepositoryPort.save(Product.builder().name("태블릿").price(new BigDecimal("800000")).stock(15).reservedStock(0).build());
        productRepositoryPort.save(Product.builder().name("무선이어폰").price(new BigDecimal("250000")).stock(50).reservedStock(0).build());
        productRepositoryPort.save(Product.builder().name("키보드").price(new BigDecimal("120000")).stock(30).reservedStock(0).build());
        productRepositoryPort.save(Product.builder().name("마우스").price(new BigDecimal("50000")).stock(100).reservedStock(0).build());
    }

    @Nested
    @DisplayName("GET /api/product/list - 상품 목록 조회")
    class GetProductList {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("페이지네이션 파라미터 없이 요청 시 기본값으로 전체 상품 목록을 반환한다")
            void getProducts_DefaultPagination_Success() throws Exception {
                // when & then
                mockMvc.perform(get("/api/product/list")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data", hasSize(6)));
            }

            @Test
            @DisplayName("limit과 offset을 지정하여 요청 시 해당 범위의 상품 목록을 반환한다")
            void getProducts_WithPagination_Success() throws Exception {
                // when & then
                mockMvc.perform(get("/api/product/list")
                                .param("limit", "2")
                                .param("offset", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data", hasSize(2)))
                        .andExpect(jsonPath("$.data[0].name", is("키보드")))
                        .andExpect(jsonPath("$.data[1].name", is("무선이어폰")));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("limit 파라미터가 음수일 경우 400 Bad Request를 반환한다")
            void getProducts_WithNegativeLimit_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/product/list")
                                .param("limit", "-1")
                                .param("offset", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }

            @Test
            @DisplayName("offset 파라미터가 음수일 경우 400 Bad Request를 반환한다")
            void getProducts_WithNegativeOffset_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/product/list")
                                .param("limit", "10")
                                .param("offset", "-1")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Nested
    @DisplayName("GET /api/product/popular - 인기 상품 조회")
    class GetPopularProducts {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("lastDays 파라미터로 요청 시 인기 상품 목록을 반환한다")
            void getPopularProducts_Success() throws Exception {
                // given
                // NOTE: 현재 구현에서는 인기도 로직이 단순 조회일 수 있으므로, 목록이 반환되는지만 확인합니다.
                // 추후 인기도 로직이 구현되면, 주문 데이터를 생성하고 순서를 검증하는 로직이 추가되어야 합니다.

                // when & then
                mockMvc.perform(get("/api/product/popular")
                                .param("days", "3")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray());
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("lastDays 파라미터가 0 이하일 경우 400 Bad Request를 반환한다")
            void getPopularProducts_WithInvalidLastDays_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/product/popular")
                                .param("days", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }
}