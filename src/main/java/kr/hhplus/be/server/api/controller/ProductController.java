package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.usecase.product.GetProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품 관리 Controller
 * 상품 목록 조회 및 인기 상품 조회 기능을 제공합니다.
 */
@Tag(name = "상품 관리", description = "상품 목록 조회 및 인기 상품 조회 API")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final GetProductListUseCase getProductListUseCase;
    private final GetPopularProductListUseCase getPopularProductListUseCase;

    /**
     * Retrieves a paginated list of products.
     *
     * @param limit  the maximum number of products to return (default is 10)
     * @param offset the starting index for pagination (default is 0)
     * @return a list of product responses representing the requested products
     */
    @ApiSuccess(summary = "상품 목록 조회")
    @GetMapping("/list")
    public List<ProductResponse> getProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // TODO: 상품 목록 조회 로직 구현
        // List<Product> products = getProductListUseCase.execute(limit, offset);
        return List.of(
                new ProductResponse(1L, "노트북", new java.math.BigDecimal("1200000"), 50),
                new ProductResponse(2L, "스마트폰", new java.math.BigDecimal("800000"), 100),
                new ProductResponse(3L, "태블릿", new java.math.BigDecimal("600000"), 30)
        );
    }

    /**
     * Retrieves a list of the top 5 most popular products from the last 3 days.
     *
     * @return a list of popular product responses
     */
    @ApiSuccess(summary = "인기 상품 조회")
    @GetMapping("/popular")
    public List<ProductResponse> getPopularProducts() {
        // TODO: 인기 상품 조회 로직 구현 (최근 3일간 상위 5개)
        // List<Product> popularProducts = getPopularProductListUseCase.execute(3);
        return List.of(
                new ProductResponse(2L, "스마트폰", new java.math.BigDecimal("800000"), 100),
                new ProductResponse(1L, "노트북", new java.math.BigDecimal("1200000"), 50),
                new ProductResponse(4L, "무선이어폰", new java.math.BigDecimal("200000"), 200),
                new ProductResponse(5L, "스마트워치", new java.math.BigDecimal("300000"), 80),
                new ProductResponse(3L, "태블릿", new java.math.BigDecimal("600000"), 30)
        );
    }
} 