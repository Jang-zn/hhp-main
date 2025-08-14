package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.api.docs.annotation.ProductApiDocs;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.service.ProductService;
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
@Validated
public class ProductController {

    private final ProductService productService;

    @ProductApiDocs(summary = "상품 목록 조회", description = "모든 상품 목록을 조회합니다")
    @GetMapping("/list")
    public List<ProductResponse> getProductList(@Valid @ModelAttribute ProductRequest request) {
        List<Product> products = productService.getProductList(request.getLimit(), request.getOffset());
        return products.stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getStock()
                ))
                .collect(Collectors.toList());
    }

    @ProductApiDocs(summary = "인기 상품 조회", description = "지정된 기간 동안의 인기 상품을 조회합니다")
    @GetMapping("/popular")
    public List<ProductResponse> getPopularProducts(@Valid @ModelAttribute ProductRequest request) {
        List<Product> popularProducts = productService.getPopularProductList(request.getDays(), request.getLimit(), request.getOffset());
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