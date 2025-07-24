package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    Optional<Product> findById(Long id);
    Product save(Product product);
    List<Product> findPopularProducts(int period);
    List<Product> findAllWithPagination(int limit, int offset);
} 