package com.segue.backend.domain.enums;

/**
 * Last Intent 의사결정 엔진(F5)이 산출하는 4가지 결과 유형.
 */
public enum ResultType {
    /** 결과 1: 정확한 제품 확인 — 타 매장 확인 요청 / 입고 확인 신청 */
    EXACT_PRODUCT,
    /** 결과 2: 비교 체험 제품 — 이 제품 확인하기 */
    COMPARISON_EXPERIENCE,
    /** 결과 3: 오늘 구매 가능한 제품 — 이 제품 확인하기 */
    TODAY_PURCHASE,
    /** 결과 4: 추가 상담 — 조건 다시 확인하기 */
    ADDITIONAL_CONSULTATION
}
