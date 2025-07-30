package kr.hhplus.be.server.unit.facade.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.facade.product.GetPopularProductListFacade;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetPopularProductListFacade 단위 테스트")
class GetPopularProductListFacadeTest {

    @Mock
    private GetPopularProductListUseCase getPopularProductListUseCase;
    
    private GetPopularProductListFacade getPopularProductListFacade;
    
    private List<Product> testPopularProducts;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getPopularProductListFacade = new GetPopularProductListFacade(getPopularProductListUseCase);
        
        Product product1 = Product.builder()
            .id(2L)
            .name("스마트폰")
            .price(new BigDecimal("800000"))
            .stock(5)
            .reservedStock(0)
            .build();
            
        Product product2 = Product.builder()
            .id(1L)
            .name("노트북")
            .price(new BigDecimal("1000000"))
            .stock(10)
            .reservedStock(0)
            .build();
            
        Product product3 = Product.builder()
            .id(4L)
            .name("무선이어폰")
            .price(new BigDecimal("150000"))
            .stock(20)
            .reservedStock(0)
            .build();
            
        testPopularProducts = List.of(product1, product2, product3);
    }

    @Nested
    @DisplayName("인기 상품 목록 조회")
    class GetPopularProductList {
        
        @Test
        @DisplayName("성공 - 정상 인기 상품 목록 조회")
        void getPopularProductList_Success() {
            // given
            int limit = 3;
            
            when(getPopularProductListUseCase.execute(limit)).thenReturn(testPopularProducts);
            
            // when
            List<Product> result = getPopularProductListFacade.getPopularProductList(limit);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getName()).isEqualTo("스마트폰");
            assertThat(result.get(1).getName()).isEqualTo("노트북");
            assertThat(result.get(2).getName()).isEqualTo("무선이어폰");
            
            verify(getPopularProductListUseCase).execute(limit);
        }
        
        @Test
        @DisplayName("성공 - 빈 인기 상품 목록")
        void getPopularProductList_EmptyList() {
            // given
            int limit = 5;
            
            when(getPopularProductListUseCase.execute(limit)).thenReturn(List.of());
            
            // when
            List<Product> result = getPopularProductListFacade.getPopularProductList(limit);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            
            verify(getPopularProductListUseCase).execute(limit);
        }
        
        @Test
        @DisplayName("성공 - 다양한 제한 수")
        void getPopularProductList_DifferentLimits() {
            // given
            int limit = 10;
            
            when(getPopularProductListUseCase.execute(limit)).thenReturn(testPopularProducts);
            
            // when
            List<Product> result = getPopularProductListFacade.getPopularProductList(limit);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(3);
            
            verify(getPopularProductListUseCase).execute(limit);
        }
        
        @Test
        @DisplayName("성공 - 소수의 인기 상품만 조회")
        void getPopularProductList_SmallLimit() {
            // given
            int limit = 1;
            
            when(getPopularProductListUseCase.execute(limit)).thenReturn(List.of(testPopularProducts.get(0)));
            
            // when
            List<Product> result = getPopularProductListFacade.getPopularProductList(limit);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("스마트폰");
            
            verify(getPopularProductListUseCase).execute(limit);
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생")
        void getPopularProductList_UseCaseException() {
            // given
            int limit = 3;
            
            when(getPopularProductListUseCase.execute(limit))
                .thenThrow(new RuntimeException("Statistics service unavailable"));
            
            // when & then
            assertThatThrownBy(() -> getPopularProductListFacade.getPopularProductList(limit))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Statistics service unavailable");
                
            verify(getPopularProductListUseCase).execute(limit);
        }
    }
}