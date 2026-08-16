package com.segue.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/** 기능명세서 5.1: CA가 고객의 데이터 이용 동의 여부를 기록할 때 보내는 요청. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRequest {

    @NotNull
    private Boolean agreed;
}
