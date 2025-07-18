package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetProductUseCase {
    
    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    
    /**
     * Retrieves a product by its unique identifier.
     *
     * @param productId the unique identifier of the product to retrieve
     * @return an {@code Optional} containing the product if found, or empty if not found or not implemented
     */
    public Optional<Product> execute(Long productId) {
        // TODO: 단일 상품 조회 로직 구현
        return Optional.empty();
    }
} 