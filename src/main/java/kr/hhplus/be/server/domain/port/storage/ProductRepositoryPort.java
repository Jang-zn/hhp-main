package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductRepositoryPort extends JpaRepository<Product, Long> {
    
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findByIds(@Param("ids") List<Long> ids);
    
    @Query("SELECT p FROM Product p WHERE p.createdAt >= :periodDate ORDER BY p.createdAt DESC")
    List<Product> findPopularProducts(@Param("periodDate") LocalDateTime periodDate, Pageable pageable);
    
    @Query("SELECT p FROM Product p ORDER BY p.createdAt DESC")
    List<Product> findAllWithPagination(Pageable pageable);
    
    // 기존 메서드 시그니처 유지를 위한 대체 메서드
    default List<Product> findPopularProducts(int period, int limit, int offset) {
        LocalDateTime periodDate = LocalDateTime.now().minusDays(period);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(offset / limit, limit);
        return findPopularProducts(periodDate, pageable);
    }
    
    default List<Product> findAllWithPagination(int limit, int offset) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(offset / limit, limit);
        return findAllWithPagination(pageable);
    }
} 