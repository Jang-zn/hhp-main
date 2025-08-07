package kr.hhplus.be.server.unit.facade.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.facade.product.GetProductListFacade;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GetProductListFacade 비즈니스 시나리오 테스트
 * 
 * Why: 상품 목록 조회 파사드가 고객의 상품 브라우징 요구사항을 올바르게 처리하는지 검증
 * How: 실제 고객의 상품 브라우징 시나리오를 반영한 파사드 레이어 테스트로 구성
 */
@DisplayName("상품 목록 조회 파사드 비즈니스 시나리오")
class GetProductListFacadeTest {

    @Mock
    private GetProductUseCase getProductUseCase;
    
    private GetProductListFacade getProductListFacade;
    
    private List<Product> testProducts;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getProductListFacade = new GetProductListFacade(getProductUseCase);
        
        Product product1 = Product.builder()
            .id(1L)
            .name("노트북")
            .price(new BigDecimal("1000000"))
            .stock(10)
            .reservedStock(0)
            .build();
            
        Product product2 = Product.builder()
            .id(2L)
            .name("스마트폰")
            .price(new BigDecimal("800000"))
            .stock(5)
            .reservedStock(0)
            .build();
            
        Product product3 = Product.builder()
            .id(3L)
            .name("태블릿")
            .price(new BigDecimal("500000"))
            .stock(15)
            .reservedStock(0)
            .build();
            
        testProducts = List.of(product1, product2, product3);
    }

    @Test
    @DisplayName("성공 - 정상 상품 목록 조회")
    void getProductList_Success() {
        // given
        int limit = 10;
        int offset = 0;

        when(getProductUseCase.execute(limit, offset)).thenReturn(testProducts);

        // when
        List<Product> result = getProductListFacade.getProductList(limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("노트북");
        assertThat(result.get(1).getName()).isEqualTo("스마트폰");
        assertThat(result.get(2).getName()).isEqualTo("태블릿");

        verify(getProductUseCase).execute(limit, offset);
    }

    @Test
    @DisplayName("성공 - 빈 상품 목록")
    void getProductList_EmptyList() {
        // given
        int limit = 10;
        int offset = 0;

        when(getProductUseCase.execute(limit, offset)).thenReturn(List.of());

        // when
        List<Product> result = getProductListFacade.getProductList(limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(getProductUseCase).execute(limit, offset);
    }

    @Test
    @DisplayName("성공 - 페이징 처리")
    void getProductList_WithPaging() {
        // given
        int limit = 5;
        int offset = 10;

        when(getProductUseCase.execute(limit, offset)).thenReturn(List.of(testProducts.get(0)));

        // when
        List<Product> result = getProductListFacade.getProductList(limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("노트북");

        verify(getProductUseCase).execute(limit, offset);
    }

    @Test
    @DisplayName("성공 - 다양한 페이지 크기")
    void getProductList_DifferentPageSizes() {
        // given
        int limit = 20;
        int offset = 0;

        when(getProductUseCase.execute(limit, offset)).thenReturn(testProducts);

        // when
        List<Product> result = getProductListFacade.getProductList(limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);

        verify(getProductUseCase).execute(limit, offset);
    }

    @Test
    @DisplayName("실패 - UseCase 실행 중 예외 발생")
    void getProductList_UseCaseException() {
        // given
        int limit = 10;
        int offset = 0;

        when(getProductUseCase.execute(limit, offset))
            .thenThrow(new RuntimeException("Database connection failed"));

        // when & then
        assertThatThrownBy(() -> getProductListFacade.getProductList(limit, offset))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection failed");

        verify(getProductUseCase).execute(limit, offset);
    }
}