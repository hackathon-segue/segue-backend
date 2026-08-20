package com.segue.backend.dto.response;

import com.segue.backend.domain.Product;
import com.segue.backend.domain.Sku;
import lombok.*;

import java.util.List;

/** F0: 제품 상세 화면. 이 제품이 갖고 있는 모든 컬러·사이즈 SKU 옵션을 함께 내려준다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private String category;
    private Integer price;
    private List<SkuOptionResponse> options;

    public static ProductDetailResponse from(Product product, List<Sku> skus) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .price(product.getPrice())
                .options(skus.stream().map(SkuOptionResponse::from).toList())
                .build();
    }
}
