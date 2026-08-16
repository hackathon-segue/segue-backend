package com.segue.backend.dto.response;

import com.segue.backend.domain.Sku;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryResponse {
    private Long skuId;
    private Long productId;
    private String productName;
    private String imageUrl;
    private String color;
    private String size;

    public static ProductSummaryResponse from(Sku sku) {
        return ProductSummaryResponse.builder()
                .skuId(sku.getId())
                .productId(sku.getProduct().getId())
                .productName(sku.getProduct().getName())
                .imageUrl(sku.getProduct().getImageUrl())
                .color(sku.getColor())
                .size(sku.getSize())
                .build();
    }
}
