package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("GetPopularProductListUseCase 단위 테스트")
class GetPopularProductListUseCaseTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;
    

    private GetPopularProductListUseCase getPopularProductListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getPopularProductListUseCase = new GetPopularProductListUseCase(productRepositoryPort);
    }

    @Test
    @DisplayName("인기 상품 목록 조회 성공")
    void getPopularProducts_Success() {
        // given
        int period = 7; // 7일
        
        List<Product> popularProducts = List.of(
                Product.builder()
                        .name("인기 노트북")
                        .price(new BigDecimal("1200000"))
                        .stock(30)
                        .reservedStock(5)
                        .build(),
                Product.builder()
                        .name("인기 스마트폰")
                        .price(new BigDecimal("800000"))
                        .stock(50)
                        .reservedStock(10)
                        .build()
        );
        
        int limit = 10;
        int offset = 0;
        
        when(productRepositoryPort.findPopularProducts(period, limit, offset)).thenReturn(popularProducts);

        // when
        List<Product> result = getPopularProductListUseCase.execute(period, limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("인기 노트북");
        assertThat(result.get(1).getName()).isEqualTo("인기 스마트폰");
        verify(productRepositoryPort, times(1)).findPopularProducts(period, limit, offset);
    }

    @ParameterizedTest
    @MethodSource("providePeriodData")
    @DisplayName("다양한 기간으로 인기 상품 조회")
    void getPopularProducts_WithDifferentPeriods(int period) {
        // given
        List<Product> popularProducts = List.of(
                Product.builder()
                        .name("상품1")
                        .price(new BigDecimal("100000"))
                        .stock(20)
                        .reservedStock(2)
                        .build()
        );
        
        // Repository mocking
        int limit = 5;
        int offset = 0;
        
        when(productRepositoryPort.findPopularProducts(period, limit, offset)).thenReturn(popularProducts);

        // when
        List<Product> result = getPopularProductListUseCase.execute(period, limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("상품1");
        verify(productRepositoryPort, times(1)).findPopularProducts(period, limit, offset);
    }

    private static Stream<Arguments> providePeriodData() {
        return Stream.of(
                Arguments.of(7),   // 7일
                Arguments.of(30),  // 30일
                Arguments.of(90)   // 90일
        );
    }
}