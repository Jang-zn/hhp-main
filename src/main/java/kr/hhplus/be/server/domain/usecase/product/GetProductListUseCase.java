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
    
    public List<Product> execute(int limit, int offset) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        
        String cacheKey = "product_list_" + limit + "_" + offset;
        
        return cachePort.get(cacheKey, List.class, () -> 
            productRepositoryPort.findAllWithPagination(limit, offset)
        );
    }
} 