package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.api.docs.annotation.ProductApiDocs;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.facade.product.GetProductListFacade;
import kr.hhplus.be.server.domain.facade.product.GetPopularProductListFacade;
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

    private final GetProductListFacade getProductListFacade;
    private final GetPopularProductListFacade getPopularProductListFacade;

    @ProductApiDocs(summary = "상품 목록 조회", description = "모든 상품 목록을 조회합니다")
    @GetMapping("/list")
    public List<ProductResponse> getProductList(@Valid ProductRequest request) {
        
        // null 요청 검증
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        
        List<Product> products = getProductListFacade.getProductList(request.getLimit(), request.getOffset());
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
    public List<ProductResponse> getPopularProducts(@Valid ProductRequest request) {
        
        // null 요청 검증
        if (request == null) {
            throw new CommonException.InvalidRequest();
        }
        
        // 최근 N일간 인기 상품 조회
        List<Product> popularProducts = getPopularProductListFacade.getPopularProductList(request.getDays());
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