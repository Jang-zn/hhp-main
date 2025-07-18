package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetPopularProductListUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    
    /**
     * Retrieves a list of popular products for the specified period.
     *
     * @param period the time period (in days or another unit) to consider for determining product popularity
     * @return a list of popular products for the given period; currently returns an empty list as the logic is not yet implemented
     */
    public List<Product> execute(int period) {
        // TODO: 인기 상품 목록 조회 로직 구현
        return List.of();
    }
} 