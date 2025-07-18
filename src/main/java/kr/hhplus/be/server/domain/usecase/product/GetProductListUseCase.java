package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetProductListUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    
    /**
     * Retrieves a paginated list of products.
     *
     * @param limit  the maximum number of products to return
     * @param offset the starting index for pagination
     * @return a list of products based on the specified limit and offset; currently returns an empty list
     */
    public List<Product> execute(int limit, int offset) {
        // TODO: 상품 목록 조회 로직 구현
        return List.of();
    }
} 