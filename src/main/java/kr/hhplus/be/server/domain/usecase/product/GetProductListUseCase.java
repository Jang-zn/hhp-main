package kr.hhplus.be.server.domain.usecase.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.StoragePort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetProductListUseCase {
    
    private final StoragePort storagePort;
    private final CachePort cachePort;
    
    public List<Product> execute(int limit, int offset) {
        // TODO: 상품 목록 조회 로직 구현
        return List.of();
    }
} 