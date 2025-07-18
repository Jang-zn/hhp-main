package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProductRepository implements ProductRepositoryPort {
    
    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }
    
    @Override
    public List<Product> findAll(int limit, int offset) {
        // TODO: 페이징 로직 구현
        return new ArrayList<>(products.values());
    }
    
    @Override
    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }
    
    @Override
    public Product updateStock(Long productId, int stock, int reservedStock) {
        Product product = products.get(productId);
        if (product != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return product;
    }
    
    @Override
    public List<Product> findPopularProducts(int period) {
        // TODO: 인기 상품 조회 로직 구현
        return new ArrayList<>();
    }
} 