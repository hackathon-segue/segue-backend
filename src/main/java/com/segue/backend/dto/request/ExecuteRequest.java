package com.segue.backend.dto.request;

import com.segue.backend.domain.enums.ActionType;
import com.segue.backend.domain.enums.ResultType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * F7~F8: /decide 응답으로 받은 결과를 그대로 담아 실행 요청 및 상담 결과 저장을 요청한다.
 * (세션 테이블을 두지 않는 무상태 설계이므로 결정 결과를 클라이언트가 들고 있다가 그대로 반환한다)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecuteRequest {

    @NotNull
    private Long customerId;

    /** 상담을 시작한 원제품 SKU */
    @NotNull
    private Long skuId;

    @NotNull
    private ResultType resultType;

    /** /decide 응답에서 받은 actionType 을 그대로 echo. 완료 메시지 매핑에 사용된다. */
    @NotNull
    private ActionType actionType;

    /** 결과 2/3 인 경우 제안된 SKU. 그 외에는 null. */
    private Long recommendedSkuId;

    /** Last Intent Card 에 표시되었던 확보 경로 설명 */
    private String pathDescription;

    /** Last Intent Card 의 "고객이 중요하게 본 조건" 문구 (consultation_result.core_conditions 로 저장) */
    private String coreConditionsSummary;
}
