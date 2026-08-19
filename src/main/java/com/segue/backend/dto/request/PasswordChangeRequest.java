package com.segue.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** 비밀번호 변경. 와이어프레임의 "현재 비밀번호 → 변경할 비밀번호" 화면에 대응한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "변경할 비밀번호를 입력해 주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String newPassword;
}
