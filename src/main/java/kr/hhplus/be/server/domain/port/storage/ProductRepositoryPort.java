package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.util.OffsetBasedPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    
    /**
     * @param period 조회 기간 (일 단위)
     * @param limit 가져올 레코드 수
     * @param offset 건너뛸 레코드 수
     * @return 인기 상품 목록
     * @throws IllegalArgumentException offset이 음수이거나 limit이 1보다 작은 경우
     */
    default List<Product> findPopularProducts(int period, int limit, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative!");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero!");
        }
        
        LocalDateTime periodDate = LocalDateTime.now().minusDays(period);
        Pageable pageable = new OffsetBasedPageRequest(offset, limit, Sort.unsorted());
        return findPopularProducts(periodDate, pageable);
    }
    
    /**
     * @param limit
     * @param offset
     * @return 상품 목록
     * @throws IllegalArgumentException offset이 음수이거나 limit이 1보다 작은 경우
     */
    default List<Product> findAllWithPagination(int limit, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative!");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero!");
        }
        
        Pageable pageable = new OffsetBasedPageRequest(offset, limit, Sort.unsorted());
        return findAllWithPagination(pageable);
    }
} 