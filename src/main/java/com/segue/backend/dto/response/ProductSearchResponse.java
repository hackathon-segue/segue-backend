package com.segue.backend.dto.response;

import com.segue.backend.domain.Product;
import com.segue.backend.domain.Sku;
import lombok.*;

import java.util.List;

/**
 * Issue #5: CA 수동 제품 검색 결과 응답.
 * 검색 조건(컬러/사이즈)에 매칭되는 SKU 옵션만 포함한다.
 * 구조는 ProductDetailResponse와 동일하되, options가 필터링된 결과.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private String category;
    private List<SkuOptionResponse> options;

    public static ProductSearchResponse from(Product product, List<Sku> filteredSkus) {
        return ProductSearchResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .options(filteredSkus.stream().map(SkuOptionResponse::from).toList())
                .build();
    }
}
