package com.segue.backend.service;

import com.segue.backend.domain.Product;
import com.segue.backend.domain.Sku;
import com.segue.backend.dto.response.ProductDetailResponse;
import com.segue.backend.dto.response.ProductListItemResponse;
import com.segue.backend.dto.response.ProductSearchResponse;
import com.segue.backend.exception.NotFoundException;
import com.segue.backend.repository.ProductRepository;
import com.segue.backend.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        List<Sku> skus = skuRepository.findByProductId(productId);
        return ProductDetailResponse.from(product, skus);
    }

    /**
     * Issue #5: CA 수동 제품 검색.
     * 제품명으로 1차 필터 후, 컬러/사이즈 조건이 있으면 SKU 레벨에서 추가 필터.
     * 매칭되는 SKU가 없는 제품은 결과에서 제외한다.
     */
    public List<ProductSearchResponse> searchProducts(String name, String color, String size) {
        List<Product> products = productRepository.findByNameContainingIgnoreCase(name);
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Sku> allSkus = skuRepository.findByProductIdIn(productIds);

        // SKU를 productId 기준으로 그룹핑
        Map<Long, List<Sku>> skusByProductId = allSkus.stream()
                .collect(Collectors.groupingBy(sku -> sku.getProduct().getId()));

        return products.stream()
                .map(product -> {
                    List<Sku> productSkus = skusByProductId.getOrDefault(product.getId(), List.of());
                    // 컬러/사이즈 조건으로 SKU 필터링
                    List<Sku> filtered = productSkus.stream()
                            .filter(sku -> color == null || sku.getColor().equalsIgnoreCase(color))
                            .filter(sku -> size == null || sku.getSize().equalsIgnoreCase(size))
                            .toList();
                    return ProductSearchResponse.from(product, filtered);
                })
                .filter(response -> !response.getOptions().isEmpty()) // 매칭 SKU 없는 제품 제외
                .toList();
    }
}
