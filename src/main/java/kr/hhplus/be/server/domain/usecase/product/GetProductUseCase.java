package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetProductUseCase {
    
    private final StoragePort storagePort;
    private final CachePort cachePort;
    
    public Optional<Product> execute(Long productId) {
        // TODO: 단일 상품 조회 로직 구현
        return Optional.empty();
    }
} 