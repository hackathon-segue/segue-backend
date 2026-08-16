package com.segue.backend.domain.enums;

/** F7 실행 요청 버튼 종류. ConsultationResult 에는 저장하지 않고 resultType 으로부터 매번 파생한다. */
public enum ActionType {
    /** 결과1 - 타 매장에 재고가 있는 경우 */
    OTHER_STORE_CHECK_REQUEST,
    /** 결과1 - 입고 예정만 있는 경우 */
    RESTOCK_CHECK_REQUEST,
    /** 결과2/3 - 현재 매장 제품 확인 */
    PRODUCT_CHECK_REQUEST,
    /** 결과4 - 조건 다시 확인 */
    RECONSULT
}
