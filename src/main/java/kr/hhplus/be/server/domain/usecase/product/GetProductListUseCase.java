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
        return productRepositoryPort.findAllWithPagination(limit, offset);
    }
} 