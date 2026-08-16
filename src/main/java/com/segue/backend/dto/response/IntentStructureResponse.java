package com.segue.backend.dto.response;

import com.segue.backend.dto.StructuredIntentDto;
import lombok.*;

/** F3 응답: 구조화된 의도 + 보충 질문 필요 여부. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentStructureResponse {
    private StructuredIntentDto structuredIntent;
    private boolean needsFollowUp;
}
