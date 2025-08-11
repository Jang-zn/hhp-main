package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 상품 조회, 인기 상품 조회 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final GetProductUseCase getProductUseCase;
    private final GetPopularProductUseCase getPopularProductUseCase;

    /**
     * 상품 목록 조회
     * 
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 상품 목록
     */
    public List<Product> getProductList(int limit, int offset) {
        return getProductUseCase.execute(limit, offset);
    }

    /**
     * 인기 상품 목록 조회
     * 
     * @param limit 조회할 상품 개수
     * @param offset 건너뛸 상품 개수
     * @return 인기 상품 목록
     */
    public List<Product> getPopularProductList(int limit, int offset) {
        return getPopularProductUseCase.execute(limit, offset);
    }
}