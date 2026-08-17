package com.segue.backend.dto.response;

import com.segue.backend.domain.Product;
import lombok.*;

/** F0: 고객 모바일 제품 목록(브라우징) 화면용 응답. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductListItemResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private String category;

    public static ProductListItemResponse from(Product product) {
        return ProductListItemResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .build();
    }
}
