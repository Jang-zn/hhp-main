package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    Optional<Product> findById(Long id);
    
    /**
     * 비관적 락을 사용하여 상품을 조회합니다.
     * SELECT ... FOR UPDATE로 배타 락을 획득하여 동시성 문제를 방지합니다.
     * 
     * @param id 상품 ID
     * @return 락이 걸린 상품 엔티티
     */
    Optional<Product> findByIdWithLock(Long id);
    
    /**
     * 여러 상품을 ID 순서로 정렬하여 비관적 락으로 조회합니다.
     * 데드락 방지를 위해 ID 순서로 락을 획득합니다.
     * 
     * @param ids 상품 ID 리스트 (정렬됨)
     * @return 락이 걸린 상품 엔티티 리스트
     */
    List<Product> findByIdsWithLock(List<Long> ids);
    
    Product save(Product product);
    List<Product> findPopularProducts(int period);
    List<Product> findAllWithPagination(int limit, int offset);
} 