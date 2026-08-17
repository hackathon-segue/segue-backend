package com.segue.backend.service;

import com.segue.backend.domain.Product;
import com.segue.backend.dto.response.ProductDetailResponse;
import com.segue.backend.dto.response.ProductListItemResponse;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.repository.ProductRepository;
import com.segue.backend.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** F0: 고객 모바일 제품 목록/상세 조회 (장바구니 담기 전 브라우징 단계) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public List<ProductListItemResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductListItemResponse::from)
                .toList();
    }

    public ProductDetailResponse getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("제품을 찾을 수 없습니다. id=" + productId));
        List<com.segue.backend.domain.Sku> skus = skuRepository.findByProductId(productId);
        return ProductDetailResponse.from(product, skus);
    }
}
