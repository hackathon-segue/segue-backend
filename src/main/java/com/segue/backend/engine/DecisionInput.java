package com.segue.backend.engine;

import com.segue.backend.domain.ProductAttribute;
import com.segue.backend.domain.Sku;
import com.segue.backend.domain.Store;
import com.segue.backend.dto.StructuredIntentDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionInput {

    /** 품절이 발생한 원제품 SKU */
    private Sku originalSku;
    private ProductAttribute originalAttribute;

    /** 원제품 SKU 가 타 매장에 있는지 / 그 매장 / 입고 예정 여부 */
    private boolean originalOtherStoreInStock;
    private Store originalOtherStoreLocation;
    private boolean originalRestockPlanned;

    /** 기능명세서 6번: 위 두 boolean 이 confirmed && 신선도 기준을 통과한 값인지 */
    private boolean originalOtherStoreReliable;
    private boolean originalRestockReliable;

    private StructuredIntentDto intent;

    /** 원제품을 제외한 카탈로그 전체 비교 후보 (현재 매장 재고 여부 포함) */
    private List<Candidate> candidates;
}
