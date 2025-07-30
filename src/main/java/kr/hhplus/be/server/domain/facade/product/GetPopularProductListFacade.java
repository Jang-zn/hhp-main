package kr.hhplus.be.server.domain.facade.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GetPopularProductListFacade {

    private final GetPopularProductListUseCase getPopularProductListUseCase;

    public GetPopularProductListFacade(GetPopularProductListUseCase getPopularProductListUseCase) {
        this.getPopularProductListUseCase = getPopularProductListUseCase;
    }

    @Transactional(readOnly = true)
    public List<Product> getPopularProductList(int limit) {
        return getPopularProductListUseCase.execute(limit);
    }
}