package kr.hhplus.be.server.domain.facade.product;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GetProductListFacade {

    private final GetProductUseCase getProductUseCase;

    public GetProductListFacade(GetProductUseCase getProductUseCase) {
        this.getProductUseCase = getProductUseCase;
    }

    @Transactional(readOnly = true)
    public List<Product> getProductList(int limit, int offset) {
        return getProductUseCase.execute(limit, offset);
    }
}