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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Repository
public interface ProductRepositoryPort extends JpaRepository<Product, Long> {
    
    /**
     * ID 목록으로 상품들을 조회합니다.
     * null이나 빈 목록에 대해 안전하게 처리하며, 결과를 ID 순으로 정렬합니다.
     * 
     * @param ids 조회할 상품 ID 목록
     * @return ID 순으로 정렬된 상품 목록 (빈 목록이면 빈 리스트 반환)
     */
    default List<Product> findByIds(Collection<Long> ids) {
        // null 또는 빈 목록 처리
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Spring Data JPA의 안전한 findAllById 사용
        List<Product> products = new ArrayList<>();
        findAllById(ids).forEach(products::add);
        
        // ID 순으로 정렬 (기존 ORDER BY p.id 동작 유지)
        products.sort(Comparator.comparing(Product::getId));
        
        return products;
    }
    
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
    
    /**
     * 인기 상품 목록을 조회합니다 (하위 호환성 유지용).
     * 
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
        // 정렬: createdAt 내림차순, id 내림차순
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                       .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = new OffsetBasedPageRequest(offset, limit, sort);
        Page<Product> page = findPopularProducts(periodDate, pageable);
        return page.getContent();
    }
    
    /**
     * 상품 목록을 페이지네이션하여 조회합니다 (하위 호환성 유지용).
     * 
     * @param limit 가져올 레코드 수
     * @param offset 건너뛸 레코드 수
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
        
        // 정렬: createdAt 내림차순, id 내림차순
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                       .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = new OffsetBasedPageRequest(offset, limit, sort);
        Slice<Product> slice = findAllWithPagination(pageable);
        return slice.getContent();
    }
} 