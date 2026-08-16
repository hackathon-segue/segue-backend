package com.segue.backend.ai.dto;

import lombok.*;

/** F4 보충 질문 생성 결과 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpQuestionDto {
    private String question;
}
