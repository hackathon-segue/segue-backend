package com.segue.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** F4: 보충 질문에 대한 고객 답변까지 반영해 의도를 다시 정리 요청한다. (최대 1회) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpAnswerRequest {

    @NotBlank
    private String utterance;

    @NotBlank
    private String followUpQuestion;

    @NotBlank
    private String followUpAnswer;
}
