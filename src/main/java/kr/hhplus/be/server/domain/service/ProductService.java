package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.CreateProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.UpdateProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.DeleteProductUseCase;
import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final GetProductUseCase getProductUseCase;
    private final GetPopularProductListUseCase getPopularProductListUseCase;
    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final ApplicationEventPublisher eventPublisher;


    /**
     * 단일 상품 조회
     * 
     * @param productId 상품 ID
     * @return 상품 정보
     */
    public Optional<Product> getProduct(Long productId) {
        log.debug("상품 조회 요청: productId={}", productId);
        return getProductUseCase.execute(productId);
    }
    
    /**
     * 상품 목록 조회
     * 
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 상품 목록
     */
    public List<Product> getProductList(int limit, int offset) {
        log.debug("상품 목록 조회 요청: limit={}, offset={}", limit, offset);
        return getProductUseCase.execute(limit, offset);
    }

    /**
     * 인기 상품 목록 조회
     * 
     * @param period 기간 (일)
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 인기 상품 목록
     */
    public List<Product> getPopularProductList(int period, int limit, int offset) {
        log.debug("인기 상품 목록 조회 요청: period={}, limit={}, offset={}", period, limit, offset);
        return getPopularProductListUseCase.execute(period, limit, offset);
    }
    
    // ========================= CRUD 메서드들 =========================
    
    /**
     * 상품 생성
     * 
     * @param name 상품명
     * @param price 가격
     * @param stock 재고
     * @return 생성된 상품
     */
    public Product createProduct(String name, BigDecimal price, Integer stock) {
        log.info("상품 생성 요청: name={}, price={}, stock={}", name, price, stock);
        
        Product createdProduct = createProductUseCase.execute(name, price, stock);
        log.info("상품 생성 완료: productId={}", createdProduct.getId());
        
        return createdProduct;
    }
    
    /**
     * 상품 수정
     * 
     * @param productId 상품 ID
     * @param name 상품명 (선택적)
     * @param price 가격 (선택적)
     * @param stock 재고 (선택적)
     * @return 수정된 상품
     */
    public Product updateProduct(Long productId, String name, BigDecimal price, Integer stock) {
        log.info("상품 수정 요청: productId={}, name={}, price={}, stock={}", productId, name, price, stock);
        
        Product updatedProduct = updateProductUseCase.execute(productId, name, price, stock);
        log.info("상품 수정 완료: productId={}", productId);
        
        return updatedProduct;
    }
    
    /**
     * 상품 삭제
     * 
     * @param productId 상품 ID
     */
    public void deleteProduct(Long productId) {
        log.info("상품 삭제 요청: productId={}", productId);
        
        try {
            // 1. UseCase를 통한 상품 삭제 (캐시 무효화 포함)
            deleteProductUseCase.execute(productId);
            
            // 2. 상품 삭제 이벤트 발행 (비동기 랭킹 처리)
            ProductUpdatedEvent deletedEvent = ProductUpdatedEvent.deleted(productId);
            eventPublisher.publishEvent(deletedEvent);
            
            log.info("상품 삭제 및 이벤트 발행 완료: productId={}", productId);
            
        } catch (Exception e) {
            log.error("상품 삭제 실패: productId={}", productId, e);
            throw e;
        }
    }
}