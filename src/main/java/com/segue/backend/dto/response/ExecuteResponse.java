package com.segue.backend.dto.response;

import lombok.*;

/** F7 실행 요청 완료 응답 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecuteResponse {
    private Long consultationResultId;
    private String completionMessage;
}
