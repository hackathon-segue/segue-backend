package com.segue.backend.domain.enums;

/** 기능명세서 7번: Last Intent Card 실행 버튼 이후의 후속 처리 상태. */
public enum ExecutionStatus {
    /** 요청 접수 (기본값, /execute 호출 시점) */
    REQUESTED,
    /** 실행 불가 (예: 타 매장도 실제로는 재고 없음으로 확인됨) */
    UNABLE,
    /** 후속 확인 필요 */
    FOLLOW_UP_NEEDED
}
