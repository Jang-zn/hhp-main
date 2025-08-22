package kr.hhplus.be.server.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import kr.hhplus.be.server.api.dto.request.ProductRequest;
import kr.hhplus.be.server.api.dto.request.CreateProductRequest;
import kr.hhplus.be.server.api.dto.request.UpdateProductRequest;
import kr.hhplus.be.server.api.dto.response.ProductResponse;
import kr.hhplus.be.server.api.docs.annotation.ProductApiDocs;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.service.ProductService;
import kr.hhplus.be.server.domain.exception.ProductException;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;


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
                .toList();
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
                .toList();
    }
    
    // ========================= CRUD API 엔드포인트들 =========================
    
    @ProductApiDocs(summary = "상품 조회", description = "특정 상품의 상세 정보를 조회합니다")
    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable @Positive Long productId) {
        var productOpt = productService.getProduct(productId);
        
        if (productOpt.isEmpty()) {
            throw new ProductException.NotFound();
        }
        
        Product product = productOpt.get();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }
    
    @ProductApiDocs(summary = "상품 생성", description = "새로운 상품을 생성합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product createdProduct = productService.createProduct(
                request.getName(),
                request.getPrice(),
                request.getStock()
        );
        
        return new ProductResponse(
                createdProduct.getId(),
                createdProduct.getName(),
                createdProduct.getPrice(),
                createdProduct.getStock()
        );
    }
    
    @ProductApiDocs(summary = "상품 수정", description = "기존 상품 정보를 수정합니다")
    @PutMapping("/{productId}")
    public ProductResponse updateProduct(
            @PathVariable @Positive Long productId,
            @Valid @RequestBody UpdateProductRequest request) {
        
        if (!request.hasUpdates()) {
            throw new ProductException.InvalidProduct("수정할 정보가 없습니다.");
        }
        
        Product updatedProduct = productService.updateProduct(
                productId,
                request.getName(),
                request.getPrice(),
                request.getStock()
        );
        
        return new ProductResponse(
                updatedProduct.getId(),
                updatedProduct.getName(),
                updatedProduct.getPrice(),
                updatedProduct.getStock()
        );
    }
    
    @ProductApiDocs(summary = "상품 삭제", description = "상품을 삭제합니다")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable @Positive Long productId) {
        productService.deleteProduct(productId);
    }
} 