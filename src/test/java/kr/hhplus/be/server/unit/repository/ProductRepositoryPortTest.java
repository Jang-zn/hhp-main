package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductRepositoryPort.findByIds 단위 테스트")
class ProductRepositoryPortTest {
    
    @Mock
    private ProductRepositoryPort productRepositoryPort;
    
    @Test
    @DisplayName("null ID 목록에 대해 빈 리스트 반환")
    void findByIds_WithNullIds_ReturnsEmptyList() {
        // given
        when(productRepositoryPort.findByIds(null)).thenCallRealMethod();
        
        // when
        List<Product> result = productRepositoryPort.findByIds(null);
        
        // then
        assertThat(result).isEmpty();
        verify(productRepositoryPort, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("빈 ID 목록에 대해 빈 리스트 반환")
    void findByIds_WithEmptyIds_ReturnsEmptyList() {
        // given
        when(productRepositoryPort.findByIds(Collections.emptyList())).thenCallRealMethod();
        
        // when
        List<Product> result = productRepositoryPort.findByIds(Collections.emptyList());
        
        // then
        assertThat(result).isEmpty();
        verify(productRepositoryPort, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("유효한 ID 목록에 대해 ID 순으로 정렬된 결과 반환")
    void findByIds_WithValidIds_ReturnsIdSortedResults() {
        // given
        List<Long> ids = Arrays.asList(3L, 1L, 2L);
        
        Product product1 = Product.builder().id(1L).name("Product 1").build();
        Product product2 = Product.builder().id(2L).name("Product 2").build();
        Product product3 = Product.builder().id(3L).name("Product 3").build();
        
        // Mock the real method call
        when(productRepositoryPort.findByIds(ids)).thenCallRealMethod();
        when(productRepositoryPort.findAllById(ids)).thenReturn(Arrays.asList(product3, product1, product2));
        
        // when
        List<Product> result = productRepositoryPort.findByIds(ids);
        
        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(1L); // ID 순으로 정렬됨
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(2).getId()).isEqualTo(3L);
        
        verify(productRepositoryPort).findAllById(ids);
    }
}