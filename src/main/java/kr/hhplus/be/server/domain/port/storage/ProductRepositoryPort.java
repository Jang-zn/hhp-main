package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    Optional<Product> findById(Long id);
    List<Product> findByIds(List<Long> ids);
    Product save(Product product);
    List<Product> findPopularProducts(int period, int limit, int offset);
    List<Product> findAllWithPagination(int limit, int offset);
} 