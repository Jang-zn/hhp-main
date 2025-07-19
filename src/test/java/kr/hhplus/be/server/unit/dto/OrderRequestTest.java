package kr.hhplus.be.server.unit.dto;

import kr.hhplus.be.server.api.dto.request.OrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderRequest 단위 테스트")
class OrderRequestTest {

    @Test
    @DisplayName("products 필드를 통한 수량 정보 설정")
    void testProductQuantitySupport() {
        // given
        Long userId = 1L;
        List<OrderRequest.ProductQuantity> products = List.of(
            new OrderRequest.ProductQuantity(1L, 2),
            new OrderRequest.ProductQuantity(2L, 3)
        );
        
        // when
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setProducts(products);
        
        // then
        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getProducts()).hasSize(2);
        assertThat(request.getProducts().get(0).getProductId()).isEqualTo(1L);
        assertThat(request.getProducts().get(0).getQuantity()).isEqualTo(2);
        assertThat(request.getProducts().get(1).getProductId()).isEqualTo(2L);
        assertThat(request.getProducts().get(1).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("기존 productIds 필드 하위 호환성 확인")
    void testLegacyProductIdsSupport() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(1L, 2L, 3L);
        
        // when
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setProductIds(productIds);
        
        // then
        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getProductIds()).hasSize(3);
        assertThat(request.getProductIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("ProductQuantity 생성자 테스트")
    void testProductQuantityConstructor() {
        // given & when
        OrderRequest.ProductQuantity productQuantity = new OrderRequest.ProductQuantity(5L, 10);
        
        // then
        assertThat(productQuantity.getProductId()).isEqualTo(5L);
        assertThat(productQuantity.getQuantity()).isEqualTo(10);
    }
}