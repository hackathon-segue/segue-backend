package com.segue.backend.dto.response;

import com.segue.backend.domain.enums.ActionType;
import com.segue.backend.domain.enums.ResultType;
import lombok.*;

/** F5(결정) + F6(카드 설명문)의 최종 응답. 실행 버튼은 이 화면에서 딱 1개만 노출한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LastIntentCardResponse {

    private ResultType resultType;

    /** 1. 고객이 중요하게 본 조건 */
    private String coreConditions;
    /** 2. 현재 가능한 다음 행동 */
    private String nextAction;
    /** 3. 해당 결과를 선택한 이유 */
    private String reason;
    /** 4. 원제품과 제안 제품/경로의 차이 */
    private String difference;

    /** 결과2/3 인 경우 제안 제품. 그 외 null. */
    private ProductSummaryResponse recommendedProduct;

    /** 확보 경로 설명 (예: "강남 신세계점 재고 확인") */
    private String pathDescription;

    private ActionType actionType;
    private String actionButtonLabel;
}
