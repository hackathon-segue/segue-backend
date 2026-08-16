package com.segue.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** F3: CA가 입력한 고객의 최초 발화를 구조화 요청할 때 보내는 요청. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentStructureRequest {

    @NotNull
    private Long storeId;

    @NotNull
    private Long skuId;

    @NotBlank
    private String utterance;
}
