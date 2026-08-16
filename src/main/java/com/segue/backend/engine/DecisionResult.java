package com.segue.backend.engine;

import com.segue.backend.domain.Sku;
import com.segue.backend.domain.Store;
import com.segue.backend.domain.enums.ResultType;
import lombok.*;

import java.util.List;

/**
 * DecisionEngine(F5)의 산출물. AI(F6 카드 생성)는 이 안의 데이터만 근거로 설명문을 작성해야 하며
 * 임의로 새로운 사실을 만들어내면 안 된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionResult {

    private ResultType resultType;

    /** 결과 2/3 에서 제시하는 매장 내 후보 SKU (없으면 null) */
    private Sku recommendedSku;

    /** 결과 1 에서 원제품을 확보할 매장 (타 매장 확인인 경우) */
    private Store targetStore;

    /** 결과 1 에서 재고가 아니라 입고 예정으로 확보하는 경우 true */
    private boolean viaRestock;

    /** 충족된 필수 조건 attributeKey 목록 */
    private List<String> matchedEssentialKeys;

    /** 충족된 선호 조건 attributeKey 목록 */
    private List<String> matchedPreferredKeys;

    /** 엔진 내부 판단 근거 코드 (로깅/디버깅 및 카드 생성 프롬프트 컨텍스트용) */
    private String reasonCode;
}
