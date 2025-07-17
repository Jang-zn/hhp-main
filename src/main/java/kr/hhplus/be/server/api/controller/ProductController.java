package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.dto.response.ProductResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @GetMapping("/list")
    public List<ProductResponse> getProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // TODO: 상품 목록 조회 로직 구현
        // List<Product> products = getProductsUseCase.execute(limit, offset);
        return List.of(
                new ProductResponse(1L, "노트북", new java.math.BigDecimal("1200000"), 50),
                new ProductResponse(2L, "스마트폰", new java.math.BigDecimal("800000"), 100),
                new ProductResponse(3L, "태블릿", new java.math.BigDecimal("600000"), 30)
        );
    }

    @GetMapping("/popular")
    public List<ProductResponse> getPopularProducts() {
        // TODO: 인기 상품 조회 로직 구현 (최근 3일간 상위 5개)
        // List<Product> popularProducts = getPopularProductsUseCase.execute();
        return List.of(
                new ProductResponse(2L, "스마트폰", new java.math.BigDecimal("800000"), 100),
                new ProductResponse(1L, "노트북", new java.math.BigDecimal("1200000"), 50),
                new ProductResponse(4L, "무선이어폰", new java.math.BigDecimal("200000"), 200),
                new ProductResponse(5L, "스마트워치", new java.math.BigDecimal("300000"), 80),
                new ProductResponse(3L, "태블릿", new java.math.BigDecimal("600000"), 30)
        );
    }
} 