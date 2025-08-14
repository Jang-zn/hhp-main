package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetPopularProductListUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    
    public List<Product> execute(int period, int limit, int offset) {
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be greater than 0");
        }
        
        // 인기 상품 조회 로직은 복잡하므로, 여기서는 단순한 예시를 제공합니다.
        // 실제로는 주문 데이터를 기반으로 집계해야 합니다.
        return productRepositoryPort.findPopularProducts(period, limit, offset);
    }
} 