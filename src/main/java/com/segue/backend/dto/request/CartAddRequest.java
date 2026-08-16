package com.segue.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** F0: 고객 모바일에서 컬러/사이즈를 선택하고 장바구니에 담을 때 보내는 요청. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartAddRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private Long productId;

    @NotBlank
    private String color;

    @NotBlank
    private String size;
}
