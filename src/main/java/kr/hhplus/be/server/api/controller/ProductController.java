package kr.hhplus.be.server.api.controller;

import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @GetMapping("/list")
    public ResponseEntity<CommonResponse<Object>> getProducts() {
        // TODO: 상품 목록 조회 로직 구현
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @GetMapping("/popular")
    public ResponseEntity<CommonResponse<Object>> getPopularProducts() {
        // TODO: 인기 상품 조회 로직 구현 (최근 3일간 상위 5개)
        return ResponseEntity.ok(CommonResponse.success(null));
    }
} 