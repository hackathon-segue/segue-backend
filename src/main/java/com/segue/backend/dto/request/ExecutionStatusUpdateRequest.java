package com.segue.backend.dto.request;

import com.segue.backend.domain.enums.ExecutionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** 기능명세서 7번: CA가 실행 버튼 이후의 처리 결과를 갱신할 때 보내는 요청. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStatusUpdateRequest {

    @NotNull
    private ExecutionStatus status;

    /** UNABLE, FOLLOW_UP_NEEDED 인 경우 필수 (사유/안내 없이는 완료 처리하지 않는다). */
    private String note;
}
