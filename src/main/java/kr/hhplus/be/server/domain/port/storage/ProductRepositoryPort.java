package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    Optional<Product> findById(String id);
    List<Product> findAll(int limit, int offset);
    Product save(Product product);
    Product updateStock(String productId, int stock, int reservedStock);
    List<Product> findPopularProducts(int period);
} 