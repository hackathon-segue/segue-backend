package com.segue.backend.dto.request;

import com.segue.backend.dto.StructuredIntentDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** F4: needsFollowUp=true 일 때 보충 질문 생성을 요청한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpQuestionRequest {

    @NotBlank
    private String utterance;

    @NotNull
    private StructuredIntentDto currentIntent;
}
