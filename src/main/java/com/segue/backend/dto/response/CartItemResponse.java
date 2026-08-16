package com.segue.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/** F2: 장바구니 항목 + SKU 기준 재고 상태 + 상담 실행 버튼 라벨. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String imageUrl;
    private String category;
    private Long skuId;
    private String color;
    private String size;

    private boolean currentStoreInStock;
    private boolean otherStoreInStock;
    private boolean restockPlanned;

    /** "제품 확인하기" | "Last Intent 시작" */
    private String actionButtonLabel;

    private LocalDateTime savedAt;
}
