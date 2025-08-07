package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.hhplus.be.server.domain.exception.ProductException;

@Repository
@Profile("test_inmemory")
public class InMemoryProductRepository implements ProductRepositoryPort {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    public void clear() {
        products.clear();
        nextId.set(1L);
    }

    @Override
    public Optional<Product> findById(Long id) {
        if (id == null) {
            throw new ProductException.InvalidProductId();
        }
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public Product save(Product product) {
        if (product == null) {
            throw new ProductException.ProductCannotBeNull();
        }
        if (product.getName() == null) {
            throw new ProductException.ProductNameCannotBeNull();
        }
        if (product.getPrice() != null && product.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ProductException.ProductPriceCannotBeNegative();
        }
        if (product.getStock() < 0) {
            throw new ProductException.ProductStockCannotBeNegative();
        }
        
        Long productId = product.getId() != null ? product.getId() : nextId.getAndIncrement();
        
        Product savedProduct = products.compute(productId, (key, existingProduct) -> {
            if (existingProduct != null) {
                product.onUpdate();
                product.setId(existingProduct.getId());
                product.setCreatedAt(existingProduct.getCreatedAt());
                return product;
            } else {
                product.onCreate();
                if (product.getId() == null) {
                    product.setId(productId);
                }
                return product;
            }
        });
        
        return savedProduct;
    }

    @Override
    public List<Product> findPopularProducts(int period) {
        // Not supported in in-memory repository
        return List.of();
    }

    @Override
    public List<Product> findAllWithPagination(int limit, int offset) {
        if (limit <= 0) {
            throw new ProductException.InvalidProductQuantityNegative();
        }
        if (offset < 0) {
            throw new ProductException.InvalidProductQuantityNegative();
        }
        
        return products.values().stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        // InMemory 환경에서는 별도의 락 구현 없이 일반 조회와 동일하게 처리
        return findById(id);
    }

    @Override
    public List<Product> findByIdsWithLock(List<Long> ids) {
        // InMemory 환경에서는 별도의 락 구현 없이 일반 배치 조회로 처리
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
} 