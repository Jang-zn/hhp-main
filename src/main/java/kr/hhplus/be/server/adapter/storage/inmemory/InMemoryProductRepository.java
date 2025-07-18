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
    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }

    @Override
    public List<Product> findPopularProducts(int period) {
        // Not supported in in-memory repository
        return List.of();
    }

    @Override
    public List<Product> findAllWithPagination(int limit, int offset) {
        return products.values().stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }
} 