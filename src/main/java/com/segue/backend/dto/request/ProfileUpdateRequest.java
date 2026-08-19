package com.segue.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 프로필 편집(성명·이메일·전화번호). 비밀번호는 별도 엔드포인트에서 변경한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {

    @NotBlank(message = "성명을 입력해 주세요.")
    private String name;

    @NotBlank(message = "이메일 주소를 입력해 주세요.")
    @Email(message = "이메일 주소 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "전화번호를 입력해 주세요.")
    private String phoneNumber;
}
