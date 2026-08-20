package com.segue.backend.controller;

import com.segue.backend.dto.response.ProductDetailResponse;
import com.segue.backend.dto.response.ProductListItemResponse;
import com.segue.backend.dto.response.ProductSearchResponse;
import com.segue.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** F0: 고객 모바일 제품 목록/상세 조회 (DB 기반, 하드코딩 금지 원칙) */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductListItemResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getProductDetail(@PathVariable Long productId) {
        return productService.getProductDetail(productId);
    }

    /** Issue #5: CA 수동 제품 검색 (제품명 필수, 컬러/사이즈 선택) */
    @GetMapping("/search")
    public List<ProductSearchResponse> searchProducts(
            @RequestParam String name,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size) {
        return productService.searchProducts(name, color, size);
    }
}
