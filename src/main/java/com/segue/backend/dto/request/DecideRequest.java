package com.segue.backend.dto.request;

import com.segue.backend.dto.StructuredIntentDto;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** F5+F6: CA·고객이 확인/수정을 마친 구조화 의도로 규칙 기반 결정 + Last Intent Card 생성을 요청한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecideRequest {

    @NotNull
    private Long storeId;

    @NotNull
    private Long skuId;

    @NotNull
    private StructuredIntentDto structuredIntent;
}
