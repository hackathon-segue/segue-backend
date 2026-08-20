package com.segue.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** 고객 모바일 회원가입. 전화번호는 CA 가 태블릿에서 고객을 조회하는 키(F1)이므로 필수다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupRequest {

    @NotBlank(message = "성명을 입력해 주세요.")
    private String name;

    @NotBlank(message = "이메일 주소를 입력해 주세요.")
    @Email(message = "이메일 주소 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "전화번호를 입력해 주세요.")
    private String phoneNumber;
}
