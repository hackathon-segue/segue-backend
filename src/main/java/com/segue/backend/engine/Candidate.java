package com.segue.backend.engine;

import com.segue.backend.domain.ProductAttribute;
import com.segue.backend.domain.Sku;
import lombok.*;

/** DecisionEngine 이 비교하는 후보 SKU 1건 (원제품 SKU 는 후보 목록에 포함하지 않는다). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {
    private Sku sku;
    private ProductAttribute attribute;
    private boolean inStockAtCurrentStore;

    /**
     * 기능명세서 6번: inStockAtCurrentStore 값이 confirmed && 신선도 기준을 통과한 값인지.
     * false 면 규칙 엔진은 이 후보를 "오늘 확정 구매 가능"으로 채택하지 않는다.
     */
    private boolean inventoryReliable;
}
