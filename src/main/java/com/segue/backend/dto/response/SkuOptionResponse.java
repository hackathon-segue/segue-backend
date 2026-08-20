package com.segue.backend.dto.response;

import com.segue.backend.domain.Sku;
import lombok.*;

/** F0: 제품 상세 화면에서 고객이 고를 수 있는 컬러·사이즈 옵션 1건. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuOptionResponse {
    private Long skuId;
    private String color;
    private String size;
    private String material;
    private Integer weightGrams;
    private String storageStructure;
    private String wearStyle;
    private boolean laptopCompatible;

    public static SkuOptionResponse from(Sku sku) {
        return SkuOptionResponse.builder()
                .skuId(sku.getId())
                .color(sku.getColor())
                .size(sku.getSize())
                .material(sku.getMaterial())
                .weightGrams(sku.getWeightGrams())
                .storageStructure(sku.getStorageStructure())
                .wearStyle(sku.getWearStyle())
                .laptopCompatible(Boolean.TRUE.equals(sku.getLaptopCompatible()))
                .build();
    }
}
