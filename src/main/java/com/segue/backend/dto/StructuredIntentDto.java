package com.segue.backend.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * F3~F4에서 사용하는 구조화된 고객 의도.
 * essential/preferred/negotiable 의 key는 ProductAttribute 필드명(camelCase) 또는 "laptopCompatible" 이어야
 * DecisionEngine이 문자열 비교로 매칭할 수 있다. 허용 key/value 어휘는 prompts/intent.txt 에 정의되어 있으며
 * DataLoader 더미 데이터의 값과 반드시 일치시킨다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredIntentDto {

    /** 사용 목적 */
    private String purpose;

    /** 필수 조건: attributeKey -> value */
    private Map<String, String> essentialConditions;

    /** 선호 조건: attributeKey -> value */
    private Map<String, String> preferredConditions;

    /** 양보 가능한 조건: attributeKey -> value */
    private Map<String, String> negotiableConditions;

    /** 구매 시급성: TODAY | THIS_WEEK | FLEXIBLE */
    private String purchaseUrgency;

    /** 실물로 확인하고 싶은 요소 (attributeKey 목록) */
    private List<String> physicalCheckAttributes;

    /** 대기 가능 여부 (null = 불명확) */
    private Boolean canWait;

    /** 타 매장 방문 가능 여부 (null = 불명확) */
    private Boolean canVisitOtherStore;

    /** AI가 판단한, 보충 질문이 필요한지 여부 */
    private boolean needsFollowUp;

    /** 보충 질문이 필요한 이유 (AI 자체 판단 근거, 내부 참고용) */
    private String followUpReason;
}
