package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.ApiMessage;
import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @GetMapping("/list")
    public ResponseEntity<CommonResponse<Object>> getProducts() {
        // TODO: 상품 목록 조회 로직 구현
        // List<Product> products = getProductsUseCase.execute();
        return CommonResponse.ok(ApiMessage.PRODUCTS_RETRIEVED.getMessage(), null); // 나중에 실제 products 데이터로 교체
    }

    @GetMapping("/popular")
    public ResponseEntity<CommonResponse<Object>> getPopularProducts() {
        // TODO: 인기 상품 조회 로직 구현 (최근 3일간 상위 5개)
        // List<Product> popularProducts = getPopularProductsUseCase.execute();
        return CommonResponse.ok(ApiMessage.POPULAR_PRODUCTS_RETRIEVED.getMessage(), null); // 나중에 실제 popularProducts 데이터로 교체
    }
} 