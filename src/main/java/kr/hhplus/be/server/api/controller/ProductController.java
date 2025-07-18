package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.api.swagger.ApiSuccess;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.usecase.product.GetProductListUseCase;
import kr.hhplus.be.server.domain.usecase.product.GetPopularProductListUseCase;
import org.springframework.validation.annotation.Validated;

import java.util.stream.Collectors;
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

    @ApiSuccess(summary = "상품 목록 조회")
    @GetMapping("/list")
    public List<ProductResponse> getProductList(@Valid ProductRequest request) {
        List<Product> products = getProductListUseCase.execute(request.getLimit(), request.getOffset());
        return products.stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getStock()
                ))
                .collect(Collectors.toList());
    }

    @ApiSuccess(summary = "인기 상품 조회")
    @GetMapping("/popular")
    public List<ProductResponse> getPopularProducts(@Valid ProductRequest request) {
        // 최근 N일간 인기 상품 조회
        List<Product> popularProducts = getPopularProductListUseCase.execute(request.getDays());
        return popularProducts.stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getStock()
                ))
                .collect(Collectors.toList());
    }
} 