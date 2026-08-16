package com.segue.backend.ai.dto;

import lombok.*;

/** F6 Last Intent Card 의 4가지 설명 요소. AI는 DecisionResult 에 없는 사실을 생성하지 않는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LastIntentCardDto {

    /** 1. 고객이 중요하게 본 조건 */
    private String coreConditions;

    /** 2. 현재 가능한 다음 행동 */
    private String nextAction;

    /** 3. 해당 결과를 선택한 이유 */
    private String reason;

    /** 4. 원제품과 제안 제품/경로의 차이 */
    private String difference;
}
