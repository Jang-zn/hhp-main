package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.util.OffsetBasedPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
    
    /**
     * @param periodDate 조회 시작 날짜
     * @param pageable 페이지네이션 및 정렬 정보
     * @return 상품 페이지
     */
    @Query("SELECT p FROM Product p WHERE p.createdAt >= :periodDate")
    Page<Product> findPopularProducts(@Param("periodDate") LocalDateTime periodDate, Pageable pageable);
    
    /**
     * @param pageable 페이지네이션 및 정렬 정보
     * @return 상품 슬라이스
     */
    @Query("SELECT p FROM Product p")
    Slice<Product> findAllWithPagination(Pageable pageable);
    
    
} 