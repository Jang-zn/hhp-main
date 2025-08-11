package kr.hhplus.be.server.domain.service;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 * 
 * 상품 조회, 인기 상품 조회 등의 기능을 제공합니다.
 * 읽기 전용 작업으로 트랜잭션이 필요하지 않아 TransactionTemplate을 사용하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final GetProductUseCase getProductUseCase;
    private final GetPopularProductListUseCase getPopularProductListUseCase;

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
        // 현재 UseCase는 period만 받으므로, 기본값으로 7일을 사용
        return getPopularProductListUseCase.execute(7);
    }
}