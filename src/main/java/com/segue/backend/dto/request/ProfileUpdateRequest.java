package com.segue.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 프로필 편집(성명·이메일·전화번호). 비밀번호 자체는 별도 엔드포인트에서 변경한다.
 *
 * 본인 확인용으로 현재 비밀번호를 함께 받는다. 토큰 기반 인가가 없는 상태라 이 값이 없으면
 * customerId 만 바꿔 보내는 것으로 남의 계정 정보를 수정할 수 있다.
 */
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

    /** 본인 확인용. 비밀번호를 바꾸는 값이 아니라 요청자가 계정 주인인지 확인하는 용도다. */
    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;
}
